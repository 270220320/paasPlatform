package com.uavplatform.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.BindCodeEntity;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.domain.DeviceStatusEntity;
import com.uavplatform.device.domain.WorkspaceEntity;
import com.uavplatform.device.dto.BindCodeVO;
import com.uavplatform.device.dto.DeviceBindRequest;
import com.uavplatform.device.dto.DeviceVO;
import com.uavplatform.device.mapper.BindCodeMapper;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.device.mapper.DeviceStatusMapper;
import com.uavplatform.device.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 设备与绑定码（U-060 / U-061）。
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> DEVICE_TYPES = Set.of("dock", "aircraft", "other");

    private final DeviceMapper deviceMapper;
    private final DeviceStatusMapper deviceStatusMapper;
    private final BindCodeMapper bindCodeMapper;
    private final WorkspaceMapper workspaceMapper;

    public PageResult<DeviceVO> page(String deviceType, String keyword, String workStatus, long page, long pageSize) {
        PermissionChecker.require("device:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);

        LambdaQueryWrapper<DeviceEntity> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(deviceType)) {
            qw.eq(DeviceEntity::getDeviceType, deviceType);
        }
        if (StringUtils.hasText(workStatus)) {
            qw.eq(DeviceEntity::getWorkStatus, workStatus);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(DeviceEntity::getSn, keyword).or().like(DeviceEntity::getName, keyword));
        }
        qw.orderByDesc(DeviceEntity::getJoinedAt);

        Page<DeviceEntity> result = deviceMapper.selectPage(new Page<>(p, size), qw);
        Map<Long, DeviceStatusEntity> statusMap = loadStatus(result.getRecords());
        List<DeviceVO> list = result.getRecords().stream()
                .map(d -> toVO(d, statusMap.get(d.getId())))
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    public BindCodeVO currentBindCode() {
        PermissionChecker.require("device:write");
        Long workspaceId = currentWorkspaceId();
        BindCodeEntity active = bindCodeMapper.selectOne(new LambdaQueryWrapper<BindCodeEntity>()
                .eq(BindCodeEntity::getWorkspaceId, workspaceId)
                .eq(BindCodeEntity::getStatus, "active")
                .gt(BindCodeEntity::getExpireAt, LocalDateTime.now())
                .orderByDesc(BindCodeEntity::getId)
                .last("LIMIT 1"));
        if (active == null) {
            return refreshBindCode();
        }
        return toBindVO(active);
    }

    @Transactional(rollbackFor = Exception.class)
    public BindCodeVO refreshBindCode() {
        PermissionChecker.require("device:write");
        Long workspaceId = currentWorkspaceId();
        List<BindCodeEntity> actives = bindCodeMapper.selectList(new LambdaQueryWrapper<BindCodeEntity>()
                .eq(BindCodeEntity::getWorkspaceId, workspaceId)
                .eq(BindCodeEntity::getStatus, "active"));
        for (BindCodeEntity item : actives) {
            item.setStatus("revoked");
            bindCodeMapper.updateById(item);
        }
        BindCodeEntity created = new BindCodeEntity();
        created.setWorkspaceId(workspaceId);
        created.setCode(generateCode());
        created.setStatus("active");
        created.setExpireAt(LocalDateTime.now().plusDays(7));
        created.setRefreshedAt(LocalDateTime.now());
        bindCodeMapper.insert(created);
        return toBindVO(created);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceVO bind(DeviceBindRequest request) {
        if (!DEVICE_TYPES.contains(request.getDeviceType())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "deviceType 仅支持 dock/aircraft/other");
        }
        WorkspaceEntity workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getWorkspaceCode, request.getWorkspaceCode().trim()));
        if (workspace == null) {
            throw new BizException(ErrorCodes.BIND_CODE_INVALID, "绑定码无效或过期");
        }
        BindCodeEntity bindCode = bindCodeMapper.selectOne(new LambdaQueryWrapper<BindCodeEntity>()
                .eq(BindCodeEntity::getWorkspaceId, workspace.getId())
                .eq(BindCodeEntity::getCode, request.getBindCode().trim())
                .eq(BindCodeEntity::getStatus, "active")
                .gt(BindCodeEntity::getExpireAt, LocalDateTime.now())
                .last("LIMIT 1"));
        if (bindCode == null) {
            throw new BizException(ErrorCodes.BIND_CODE_INVALID, "绑定码无效或过期");
        }
        Long snCount = deviceMapper.selectCount(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getSn, request.getSn().trim()));
        if (snCount != null && snCount > 0) {
            throw new BizException(ErrorCodes.DEVICE_SN_EXISTS, "设备 SN 已存在");
        }

        DeviceEntity device = new DeviceEntity();
        device.setWorkspaceId(workspace.getId());
        device.setSn(request.getSn().trim());
        device.setName(request.getName().trim());
        device.setDeviceType(request.getDeviceType());
        device.setModel(request.getModel().trim());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setFirmwareUpgradeStatus("latest");
        device.setWorkStatus("offline");
        device.setJoinedAt(LocalDateTime.now());
        deviceMapper.insert(device);

        DeviceStatusEntity status = new DeviceStatusEntity();
        status.setDeviceId(device.getId());
        status.setOnlineState("offline");
        deviceStatusMapper.insert(status);
        return toVO(device, status);
    }

    private Long currentWorkspaceId() {
        LoginUser loginUser = UserContext.get();
        return loginUser == null || loginUser.getWorkspaceId() == null ? 1L : loginUser.getWorkspaceId();
    }

    private String generateCode() {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "BIND-" + raw.substring(0, 4) + "-" + raw.substring(4);
    }

    private Map<Long, DeviceStatusEntity> loadStatus(List<DeviceEntity> devices) {
        Map<Long, DeviceStatusEntity> map = new HashMap<>();
        for (DeviceEntity device : devices) {
            DeviceStatusEntity status = deviceStatusMapper.selectById(device.getId());
            if (status != null) {
                map.put(device.getId(), status);
            }
        }
        return map;
    }

    private BindCodeVO toBindVO(BindCodeEntity entity) {
        WorkspaceEntity workspace = workspaceMapper.selectById(entity.getWorkspaceId());
        return BindCodeVO.builder()
                .code(entity.getCode())
                .workspaceId(entity.getWorkspaceId())
                .workspaceCode(workspace == null ? null : workspace.getWorkspaceCode())
                .expireAt(entity.getExpireAt() == null ? null : entity.getExpireAt().format(FMT))
                .status(entity.getStatus())
                .build();
    }

    private DeviceVO toVO(DeviceEntity device, DeviceStatusEntity status) {
        return DeviceVO.builder()
                .id(device.getId())
                .sn(device.getSn())
                .gatewaySn(device.getGatewaySn())
                .name(device.getName())
                .deviceType(device.getDeviceType())
                .model(device.getModel())
                .firmwareVersion(device.getFirmwareVersion())
                .firmwareUpgradeStatus(device.getFirmwareUpgradeStatus())
                .flightSafetyDbVersion(device.getFlightSafetyDbVersion())
                .workStatus(device.getWorkStatus())
                .joinedAt(device.getJoinedAt() == null ? null : device.getJoinedAt().format(FMT))
                .onlineAt(device.getOnlineAt() == null ? null : device.getOnlineAt().format(FMT))
                .onlineState(status == null ? null : status.getOnlineState())
                .build();
    }
}
