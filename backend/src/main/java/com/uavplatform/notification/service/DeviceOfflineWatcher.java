package com.uavplatform.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.domain.DeviceStatusEntity;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.device.mapper.DeviceStatusMapper;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 设备离线监视：周期拉司空设备拓扑，对比本地 t_device_status，
 * 仅在「上一轮确实在线 → 本轮明确离线」的转变时发告警通知。
 * <p>防误报取舍（对齐 OSD 快照残留教训）：
 * <ul>
 *   <li>SDK 不可用 / 拉取异常 → 跳过本轮，不改状态不发通知；</li>
 *   <li>设备未出现在司空返回列表中 → 不视为离线（可能在其他页/被过滤）；</li>
 *   <li>服务重启首轮用库内 online_state 做基线，只对转变发通知，避免重启风暴。</li>
 * </ul>
 */
@Slf4j(topic = "aiflow.notification")
@Component
@RequiredArgsConstructor
public class DeviceOfflineWatcher {

    private final Fh2SdkClient fh2SdkClient;
    private final DeviceMapper deviceMapper;
    private final DeviceStatusMapper deviceStatusMapper;
    private final NotificationService notificationService;

    /** deviceId → 上轮在线状态 */
    private final ConcurrentMap<Long, Boolean> lastOnlineByDevice = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${uav.notification.device-watch-interval-ms:60000}")
    public void watch() {
        if (!fh2SdkClient.available()) {
            return;
        }
        List<Fh2DeviceItemVO> remote;
        try {
            remote = fh2SdkClient.listDevices();
        } catch (Exception e) {
            log.debug("[device-watch] listDevices fail, skip round: {}", e.getMessage());
            return;
        }
        if (remote == null || remote.isEmpty()) {
            return;
        }
        Map<String, DeviceEntity> localBySn = new HashMap<>();
        for (DeviceEntity device : deviceMapper.selectList(new LambdaQueryWrapper<DeviceEntity>()
                .isNotNull(DeviceEntity::getSn))) {
            if (StringUtils.hasText(device.getSn())) {
                localBySn.put(device.getSn().trim(), device);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Fh2DeviceItemVO item : remote) {
            if (item == null || !StringUtils.hasText(item.getSn())) {
                continue;
            }
            DeviceEntity device = localBySn.get(item.getSn().trim());
            if (device == null || device.getId() == null) {
                // 未同步到本地的设备不监听（同步设备后自动纳入）
                continue;
            }
            boolean online = item.isOnline();
            boolean prev = lastOnlineByDevice.computeIfAbsent(device.getId(), k -> loadDbOnlineState(k));
            if (online == prev) {
                continue;
            }
            lastOnlineByDevice.put(device.getId(), online);
            updateStatus(device.getId(), online, now);
            if (!online) {
                String name = StringUtils.hasText(device.getName()) ? device.getName() : device.getSn();
                String kind = "aircraft".equals(device.getDeviceType()) ? "飞行器" : "机场";
                notificationService.publish(device.getWorkspaceId(), "device.offline", "warning",
                        "设备离线：" + name,
                        kind + "「" + name + "」（SN " + device.getSn() + "）已离线，请检查网络与供电。",
                        "device", device.getId());
                log.info("[device-watch] device {} ({}) offline, notified", device.getId(), device.getSn());
            }
        }
    }

    private boolean loadDbOnlineState(Long deviceId) {
        DeviceStatusEntity status = deviceStatusMapper.selectById(deviceId);
        return status != null && "online".equalsIgnoreCase(status.getOnlineState());
    }

    private void updateStatus(Long deviceId, boolean online, LocalDateTime now) {
        DeviceStatusEntity status = deviceStatusMapper.selectById(deviceId);
        if (status == null) {
            status = new DeviceStatusEntity();
            status.setDeviceId(deviceId);
            status.setOnlineState(online ? "online" : "offline");
            status.setUpdatedAt(now);
            deviceStatusMapper.insert(status);
            return;
        }
        status.setOnlineState(online ? "online" : "offline");
        status.setUpdatedAt(now);
        deviceStatusMapper.updateById(status);
    }
}
