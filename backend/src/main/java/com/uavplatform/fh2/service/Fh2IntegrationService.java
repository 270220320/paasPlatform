package com.uavplatform.fh2.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.domain.DeviceStatusEntity;
import com.uavplatform.device.dto.DeviceVO;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.device.mapper.DeviceStatusMapper;
import com.uavplatform.fh2.config.Fh2Properties;
import com.uavplatform.fh2.dto.Fh2AircraftLocationVO;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;
import com.uavplatform.fh2.dto.PaasBootstrapVO;
import com.uavplatform.fh2.sdk.Fh2RuntimeConfigHolder;
import com.uavplatform.fh2.sdk.ReflectiveFh2SdkClient;
import com.uavplatform.fh2.util.Fh2TokenClaims;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.domain.WaylineFolderEntity;
import com.uavplatform.wayline.dto.WaylineListVO;
import com.uavplatform.wayline.mapper.WaylineFolderMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Fh2IntegrationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Fh2RuntimeConfigHolder configHolder;
    private final ReflectiveFh2SdkClient sdkClient;
    private final DeviceMapper deviceMapper;
    private final DeviceStatusMapper deviceStatusMapper;
    private final WaylineMapper waylineMapper;
    private final WaylineFolderMapper waylineFolderMapper;

    public PaasBootstrapVO bootstrap() {
        PermissionChecker.requireAny("device:read", "wayline:read", "dashboard:read");
        Fh2Properties cfg = configHolder.effective();
        Fh2TokenClaims.Claims claims = Fh2TokenClaims.parse(cfg.getUserToken());
        String orgUuid = claims.organizationUuid();
        String binding = StringUtils.hasText(cfg.getBindingUuid()) ? cfg.getBindingUuid() : orgUuid;
        String prefix = StringUtils.hasText(cfg.getObjectKeyPrefix())
                ? cfg.getObjectKeyPrefix()
                : (orgUuid != null ? orgUuid : binding);
        return PaasBootstrapVO.builder()
                .enabled(cfg.isEnabled())
                .frontHost(trimSlash(cfg.getFrontHost()))
                .serverHost(trimSlash(cfg.getServerHost()))
                .gatewayBase(trimSlash(cfg.getGatewayBase()))
                .wssUrl(cfg.getWssUrl())
                .userToken(cfg.getUserToken())
                .bindingUuid(binding)
                .organizationUuid(orgUuid)
                .projectUuid(claims.projectUuid())
                .objectKeyPrefix(prefix)
                .waylineSdkCss(cfg.getWaylineSdkCss())
                .waylineSdkJs(cfg.getWaylineSdkJs())
                .deviceSdkCss(cfg.getDeviceSdkCss())
                .deviceSdkJs(cfg.getDeviceSdkJs())
                .replaySdkCss(cfg.getReplaySdkCss())
                .replaySdkJs(cfg.getReplaySdkJs())
                .authHeaderName("X-User-Token")
                .build();
    }

    public Map<String, Object> sdkStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", configHolder.effective().isEnabled());
        m.put("sdkAvailable", sdkClient.available());
        m.put("initError", sdkClient.initError().orElse(null));
        return m;
    }

    public void reloadSdk() {
        PermissionChecker.require("role:manage");
        sdkClient.refresh();
    }

    @Transactional(rollbackFor = Exception.class)
    public int syncDevices() {
        PermissionChecker.require("device:write");
        ensureSdk();
        List<Fh2DeviceItemVO> remote = sdkClient.listDevices();
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Fh2DeviceItemVO item : remote) {
            if (!StringUtils.hasText(item.getSn())) {
                continue;
            }
            DeviceEntity exist = deviceMapper.selectOne(new LambdaQueryWrapper<DeviceEntity>()
                    .eq(DeviceEntity::getSn, item.getSn()).last("LIMIT 1"));
            if (exist == null) {
                DeviceEntity d = new DeviceEntity();
                d.setWorkspaceId(1L);
                d.setSn(item.getSn());
                d.setGatewaySn(item.getGatewaySn());
                d.setName(item.getName());
                d.setDeviceType(item.getDeviceType());
                d.setModel(item.getModel());
                d.setWorkStatus(item.getWorkStatus() == null ? "idle" : item.getWorkStatus());
                d.setSource("fh2");
                d.setJoinedAt(now);
                d.setOnlineAt(item.isOnline() ? now : null);
                d.setFh2SyncedAt(now);
                deviceMapper.insert(d);
                DeviceStatusEntity st = new DeviceStatusEntity();
                st.setDeviceId(d.getId());
                st.setOnlineState(item.isOnline() ? "online" : "offline");
                st.setUpdatedAt(now);
                deviceStatusMapper.insert(st);
            } else {
                exist.setName(item.getName());
                exist.setDeviceType(item.getDeviceType());
                exist.setModel(item.getModel());
                exist.setGatewaySn(item.getGatewaySn());
                exist.setWorkStatus(item.getWorkStatus());
                exist.setSource("fh2");
                exist.setFh2SyncedAt(now);
                if (item.isOnline()) {
                    exist.setOnlineAt(now);
                }
                deviceMapper.updateById(exist);
                DeviceStatusEntity st = deviceStatusMapper.selectById(exist.getId());
                if (st == null) {
                    st = new DeviceStatusEntity();
                    st.setDeviceId(exist.getId());
                    st.setOnlineState(item.isOnline() ? "online" : "offline");
                    st.setUpdatedAt(now);
                    deviceStatusMapper.insert(st);
                } else {
                    st.setOnlineState(item.isOnline() ? "online" : "offline");
                    st.setUpdatedAt(now);
                    deviceStatusMapper.updateById(st);
                }
            }
            n++;
        }
        return n;
    }

    @Transactional(rollbackFor = Exception.class)
    public int syncWaylines() {
        PermissionChecker.require("wayline:write");
        ensureSdk();
        List<Fh2WaylineItemVO> remote = sdkClient.listWaylines(0, 100);
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Fh2WaylineItemVO item : remote) {
            if (!StringUtils.hasText(item.getWaylineUuid())) {
                continue;
            }
            WaylineEntity exist = waylineMapper.selectOne(new LambdaQueryWrapper<WaylineEntity>()
                    .eq(WaylineEntity::getFh2WaylineUuid, item.getWaylineUuid()).last("LIMIT 1"));
            if (exist == null) {
                WaylineEntity w = new WaylineEntity();
                w.setWorkspaceId(1L);
                w.setFolderId(ensureDefaultFolderId());
                w.setName(blank(item.getWaylineName(), "未命名航线"));
                w.setAircraftModel(blank(item.getDroneType(), "unknown"));
                w.setWaylineType("waypoint");
                w.setStatus("published");
                w.setFh2WaylineUuid(item.getWaylineUuid());
                w.setSource("fh2");
                w.setCreatedAt(now);
                w.setUpdatedAt(toLdt(item.getUpdatedAt(), now));
                waylineMapper.insert(w);
            } else {
                if (StringUtils.hasText(item.getWaylineName())) {
                    exist.setName(item.getWaylineName().trim());
                } else if (!StringUtils.hasText(exist.getName())
                        || exist.getName().equals(item.getWaylineUuid())) {
                    exist.setName("未命名航线");
                }
                exist.setAircraftModel(blank(item.getDroneType(), exist.getAircraftModel()));
                exist.setSource("fh2");
                exist.setUpdatedAt(toLdt(item.getUpdatedAt(), now));
                waylineMapper.updateById(exist);
            }
            n++;
        }
        return n;
    }

    /**
     * 前端 WaylineEditor 保存成功后回写本地镜像（名称、文件夹、删除状态由本地库维护）。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaylineListVO upsertWaylineFromEditor(String waylineUuid, String name, Long folderId) {
        PermissionChecker.require("wayline:write");
        if (!StringUtils.hasText(waylineUuid)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "waylineUuid 必填");
        }
        LocalDateTime now = LocalDateTime.now();
        WaylineEntity exist = waylineMapper.selectOne(new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getFh2WaylineUuid, waylineUuid).last("LIMIT 1"));
        if (exist == null) {
            exist = new WaylineEntity();
            exist.setWorkspaceId(1L);
            exist.setFolderId(folderId != null ? folderId : ensureDefaultFolderId());
            exist.setName(blank(name, "未命名航线"));
            exist.setAircraftModel("unknown");
            exist.setWaylineType("waypoint");
            exist.setStatus("published");
            exist.setFh2WaylineUuid(waylineUuid);
            exist.setSource("fh2");
            exist.setDeleted(0);
            exist.setCreatedAt(now);
            exist.setUpdatedAt(now);
            waylineMapper.insert(exist);
        } else {
            if (StringUtils.hasText(name)) {
                exist.setName(name.trim());
            }
            if (folderId != null) {
                exist.setFolderId(folderId);
            } else if (exist.getFolderId() == null) {
                exist.setFolderId(ensureDefaultFolderId());
            }
            exist.setSource("fh2");
            exist.setUpdatedAt(now);
            waylineMapper.updateById(exist);
        }
        return WaylineListVO.builder()
                .id(exist.getId())
                .name(exist.getName())
                .aircraftModel(exist.getAircraftModel())
                .waylineType(exist.getWaylineType())
                .status(exist.getStatus())
                .fh2WaylineUuid(exist.getFh2WaylineUuid())
                .source(exist.getSource())
                .folderId(exist.getFolderId())
                .updatedAt(exist.getUpdatedAt() == null ? null : exist.getUpdatedAt().format(FMT))
                .build();
    }

    public PageResult<DeviceVO> previewRemoteDevices() {
        PermissionChecker.require("device:read");
        ensureSdk();
        List<DeviceVO> list = sdkClient.listDevices().stream().map(d -> DeviceVO.builder()
                .sn(d.getSn())
                .gatewaySn(d.getGatewaySn())
                .name(d.getName())
                .deviceType(d.getDeviceType())
                .model(d.getModel())
                .workStatus(d.getWorkStatus())
                .onlineState(d.isOnline() ? "online" : "offline")
                .build()).collect(Collectors.toList());
        return PageResult.of(list, list.size(), 1, list.size());
    }

    /**
     * 首页地图：在飞飞行器实时位置。
     * 本地常只有机场记录，会把 dock/aircraft 一并交给 SDK，由 getDeviceDetailInfo 解析飞机 SN。
     */
    public List<Fh2AircraftLocationVO> listFlyingAircraftLocations() {
        PermissionChecker.requireAny("dashboard:read", "device:read");
        if (!configHolder.effective().isEnabled() || !sdkClient.available()) {
            return List.of();
        }
        try {
            List<DeviceEntity> local = deviceMapper.selectList(new LambdaQueryWrapper<DeviceEntity>()
                    .orderByDesc(DeviceEntity::getId));
            List<Fh2DeviceItemVO> seeds = local == null ? List.of() : local.stream()
                    .filter(d -> StringUtils.hasText(d.getSn()))
                    .map(d -> Fh2DeviceItemVO.builder()
                            .sn(d.getSn())
                            .name(d.getName())
                            .gatewaySn(d.getGatewaySn())
                            .model(d.getModel())
                            .deviceType(d.getDeviceType())
                            .workStatus(d.getWorkStatus())
                            .online(true)
                            .build())
                    .collect(Collectors.toList());
            return sdkClient.listFlyingAircraftLocations(seeds);
        } catch (Exception e) {
            log.warn("[FH2] listFlyingAircraftLocations failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 确保至少有一个默认文件夹，供本地航线镜像归属。 */
    private Long ensureDefaultFolderId() {
        final long workspaceId = 1L;
        WaylineFolderEntity exist = waylineFolderMapper.selectOne(new LambdaQueryWrapper<WaylineFolderEntity>()
                .eq(WaylineFolderEntity::getWorkspaceId, workspaceId)
                .eq(WaylineFolderEntity::getName, "默认文件夹")
                .last("LIMIT 1"));
        if (exist != null) {
            return exist.getId();
        }
        WaylineFolderEntity first = waylineFolderMapper.selectOne(new LambdaQueryWrapper<WaylineFolderEntity>()
                .eq(WaylineFolderEntity::getWorkspaceId, workspaceId)
                .orderByAsc(WaylineFolderEntity::getId)
                .last("LIMIT 1"));
        if (first != null) {
            return first.getId();
        }
        WaylineFolderEntity folder = new WaylineFolderEntity();
        folder.setWorkspaceId(workspaceId);
        folder.setName("默认文件夹");
        waylineFolderMapper.insert(folder);
        return folder.getId();
    }

    private void ensureSdk() {
        if (!configHolder.effective().isEnabled()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "FH2 集成未启用");
        }
        if (!sdkClient.available()) {
            throw new BizException(ErrorCodes.INTERNAL,
                    "FH2 SDK 不可用: " + sdkClient.initError().orElse("unknown"));
        }
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("/+$", "");
    }

    private static String blank(String v, String def) {
        return StringUtils.hasText(v) ? v : def;
    }

    private static LocalDateTime toLdt(Long epochMs, LocalDateTime def) {
        if (epochMs == null || epochMs <= 0) {
            return def;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
