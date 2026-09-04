package com.uavplatform.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.safety.domain.SafetyAreaEntity;
import com.uavplatform.safety.domain.SafetyDeviceSyncEntity;
import com.uavplatform.safety.dto.SafetyAreaSaveRequest;
import com.uavplatform.safety.dto.SafetyAreaVO;
import com.uavplatform.safety.dto.SafetyCoordinateDTO;
import com.uavplatform.safety.dto.SafetyDeviceSyncVO;
import com.uavplatform.safety.dto.SafetySyncRequest;
import com.uavplatform.safety.mapper.SafetyAreaMapper;
import com.uavplatform.safety.mapper.SafetyDeviceSyncMapper;
import com.uavplatform.safety.remote.Dji2SafetyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SafetyAreaMapper safetyAreaMapper;
    private final SafetyDeviceSyncMapper safetyDeviceSyncMapper;
    private final Dji2SafetyClient dji2SafetyClient;

    public List<SafetyAreaVO> listAreas() {
        PermissionChecker.requireAny("map:read", "dashboard:read");
        return safetyAreaMapper.selectList(new LambdaQueryWrapper<SafetyAreaEntity>()
                        .eq(SafetyAreaEntity::getWorkspaceId, workspaceId())
                        .orderByDesc(SafetyAreaEntity::getUpdatedAt)
                        .orderByDesc(SafetyAreaEntity::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyAreaVO create(SafetyAreaSaveRequest request) {
        PermissionChecker.require("map:write");
        validateGeometry(request);
        SafetyAreaEntity entity = new SafetyAreaEntity();
        entity.setWorkspaceId(workspaceId());
        applyRequest(entity, request);
        entity.setSyncStatus("pending");
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        safetyAreaMapper.insert(entity);
        markDevicesNeedResync();
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyAreaVO update(Long id, SafetyAreaSaveRequest request) {
        PermissionChecker.require("map:write");
        SafetyAreaEntity entity = requireArea(id);
        validateGeometry(request);
        applyRequest(entity, request);
        entity.setSyncStatus("pending");
        entity.setUpdatedBy(userId());
        safetyAreaMapper.updateById(entity);
        markDevicesNeedResync();
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("map:write");
        SafetyAreaEntity entity = requireArea(id);
        if (StringUtils.hasText(entity.getFh2FlightAreaId()) && dji2SafetyClient.configured()) {
            try {
                dji2SafetyClient.deleteArea(entity.getFh2FlightAreaId());
            } catch (Exception e) {
                log.warn("[safety] remote delete {} failed: {}", entity.getFh2FlightAreaId(), e.getMessage());
                throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR,
                        "司空删除限飞区失败：" + e.getMessage());
            }
        }
        safetyAreaMapper.deleteById(entity.getId());
        markDevicesNeedResync();
    }

    public List<SafetyDeviceSyncVO> listSyncStatus(List<String> deviceSns) {
        PermissionChecker.require("map:read");
        if (CollectionUtils.isEmpty(deviceSns)) {
            return List.of();
        }
        List<String> sns = deviceSns.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (sns.isEmpty()) {
            return List.of();
        }

        // 优先用司空 syncstatus 刷新本地状态
        if (dji2SafetyClient.configured()) {
            try {
                List<Map<String, Object>> remote = dji2SafetyClient.syncStatus(sns);
                String hash = computeZoneHash();
                for (Map<String, Object> item : remote) {
                    String sn = firstText(item, "deviceSn", "sn");
                    if (!StringUtils.hasText(sn)) {
                        continue;
                    }
                    String status = mapRemoteSyncStatus(firstText(item, "syncStatus", "status"));
                    String msg = firstText(item, "message", "syncMsg");
                    if (!StringUtils.hasText(msg) && item.get("reason") != null) {
                        msg = "reason: " + item.get("reason");
                    }
                    upsertDeviceSyncRow(sn, null, status, msg,
                            "synchronized".equals(status) ? hash : null);
                }
            } catch (Exception e) {
                log.warn("[safety] pull syncstatus failed: {}", e.getMessage());
            }
        }

        Map<String, SafetyDeviceSyncEntity> existing = safetyDeviceSyncMapper.selectList(
                        new LambdaQueryWrapper<SafetyDeviceSyncEntity>()
                                .eq(SafetyDeviceSyncEntity::getWorkspaceId, workspaceId())
                                .in(SafetyDeviceSyncEntity::getDeviceSn, sns))
                .stream()
                .collect(Collectors.toMap(SafetyDeviceSyncEntity::getDeviceSn, e -> e, (a, b) -> a));

        String hash = computeZoneHash();
        List<SafetyDeviceSyncVO> result = new ArrayList<>();
        for (String sn : sns) {
            SafetyDeviceSyncEntity row = existing.get(sn);
            if (row == null) {
                result.add(SafetyDeviceSyncVO.builder()
                        .deviceSn(sn)
                        .nickname(sn)
                        .syncStatus("wait_sync")
                        .syncMsg("尚未同步到该设备")
                        .build());
                continue;
            }
            String status = row.getSyncStatus();
            String msg = row.getSyncMsg();
            if ("synchronized".equals(status) && StringUtils.hasText(row.getZoneHash())
                    && !Objects.equals(row.getZoneHash(), hash)) {
                status = "wait_sync";
                msg = "限飞区已变更，需重新同步";
            }
            result.add(SafetyDeviceSyncVO.builder()
                    .deviceSn(row.getDeviceSn())
                    .nickname(StringUtils.hasText(row.getNickname()) ? row.getNickname() : row.getDeviceSn())
                    .syncStatus(status)
                    .syncMsg(msg)
                    .zoneHash(row.getZoneHash())
                    .updatedAt(row.getUpdatedAt() == null ? null : row.getUpdatedAt().format(FMT))
                    .build());
        }
        return result;
    }

    /**
     * 同步流程：
     * 1) 将本地限飞区 upsert 到司空 dji2 /safety
     * 2) 调用 /safety/sync 下发到选中机场
     * 3) 回写本地设备同步状态
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SafetyDeviceSyncVO> syncToDevices(SafetySyncRequest request) {
        PermissionChecker.require("map:write");
        if (request == null || CollectionUtils.isEmpty(request.getDeviceSns())) {
            throw new BizException(ErrorCodes.NO_DEVICE_TO_SYNC, "请选择需要同步的设备");
        }
        if (!dji2SafetyClient.configured()) {
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR,
                    "司空 SDK 未初始化，且未配置旧 route-api-base/userToken，无法下发到机场");
        }

        List<String> sns = request.getDeviceSns().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (sns.isEmpty()) {
            throw new BizException(ErrorCodes.NO_DEVICE_TO_SYNC, "请选择需要同步的设备");
        }

        // 先标记同步中
        for (String sn : sns) {
            String nickname = request.getNicknames() == null ? null : request.getNicknames().get(sn);
            upsertDeviceSyncRow(sn, nickname, "synchronizing", "正在推送到司空并下发机场…", null);
        }

        try {
            pushLocalAreasToRemote();
            List<Map<String, Object>> syncResults = dji2SafetyClient.syncToDevices(sns);
            String hash = computeZoneHash();
            Map<String, Map<String, Object>> bySn = new LinkedHashMap<>();
            for (Map<String, Object> item : syncResults) {
                String sn = firstText(item, "deviceSn", "sn");
                if (StringUtils.hasText(sn)) {
                    bySn.put(sn.trim(), item);
                }
            }

            List<SafetyDeviceSyncVO> out = new ArrayList<>();
            for (String sn : sns) {
                String nickname = request.getNicknames() == null ? null : request.getNicknames().get(sn);
                Map<String, Object> item = bySn.get(sn);
                String status;
                String msg;
                if (item == null) {
                    status = "wait_sync";
                    msg = "已下发，等待设备确认";
                } else {
                    Object code = item.get("code");
                    status = mapRemoteSyncStatus(firstText(item, "status", "syncStatus"));
                    if ("unknown".equals(status) && code != null && "0".equals(String.valueOf(code))) {
                        status = "synchronized";
                    }
                    if ("unknown".equals(status)) {
                        status = "wait_sync";
                    }
                    msg = firstText(item, "message", "syncMsg");
                    if (!StringUtils.hasText(msg)) {
                        msg = "synchronized".equals(status) ? "同步成功" : "已下发，等待设备确认";
                    }
                }
                SafetyDeviceSyncEntity row = upsertDeviceSyncRow(sn, nickname, status, msg,
                        "synchronized".equals(status) ? hash : null);
                out.add(toSyncVO(row));
            }

            // 再拉一次 syncstatus 对齐设备侧真实状态
            try {
                List<Map<String, Object>> remoteStatus = dji2SafetyClient.syncStatus(sns);
                for (Map<String, Object> item : remoteStatus) {
                    String sn = firstText(item, "deviceSn", "sn");
                    if (!StringUtils.hasText(sn)) {
                        continue;
                    }
                    String status = mapRemoteSyncStatus(firstText(item, "syncStatus", "status"));
                    String msg = firstText(item, "message", "syncMsg");
                    SafetyDeviceSyncEntity row = upsertDeviceSyncRow(sn, null, status, msg,
                            "synchronized".equals(status) ? hash : null);
                    for (int i = 0; i < out.size(); i++) {
                        if (sn.equals(out.get(i).getDeviceSn())) {
                            out.set(i, toSyncVO(row));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[safety] post-sync syncstatus failed: {}", e.getMessage());
            }

            List<SafetyAreaEntity> areas = safetyAreaMapper.selectList(new LambdaQueryWrapper<SafetyAreaEntity>()
                    .eq(SafetyAreaEntity::getWorkspaceId, workspaceId()));
            for (SafetyAreaEntity area : areas) {
                area.setSyncStatus("synced");
                safetyAreaMapper.updateById(area);
            }
            return out;
        } catch (BizException e) {
            for (String sn : sns) {
                String nickname = request.getNicknames() == null ? null : request.getNicknames().get(sn);
                upsertDeviceSyncRow(sn, nickname, "fail", e.getMessage(), null);
            }
            throw e;
        } catch (Exception e) {
            for (String sn : sns) {
                String nickname = request.getNicknames() == null ? null : request.getNicknames().get(sn);
                upsertDeviceSyncRow(sn, nickname, "fail", e.getMessage(), null);
            }
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, "同步到机场失败：" + e.getMessage());
        }
    }

    /** 本地限飞区全量 upsert 到司空安全区 */
    private void pushLocalAreasToRemote() {
        List<SafetyAreaEntity> areas = safetyAreaMapper.selectList(new LambdaQueryWrapper<SafetyAreaEntity>()
                .eq(SafetyAreaEntity::getWorkspaceId, workspaceId())
                .orderByAsc(SafetyAreaEntity::getId));
        for (SafetyAreaEntity area : areas) {
            Map<String, Object> content = parseContentMap(area.getContent());
            if (StringUtils.hasText(area.getFh2FlightAreaId())) {
                dji2SafetyClient.updateArea(area.getFh2FlightAreaId(), area.getName(), content);
            } else {
                String remoteId = dji2SafetyClient.createArea(area.getName(), content);
                area.setFh2FlightAreaId(remoteId);
                safetyAreaMapper.updateById(area);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContentMap(String contentJson) {
        try {
            Map<String, Object> map = JSON.readValue(contentJson, new TypeReference<Map<String, Object>>() {});
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "限飞区内容无法推送到司空");
        }
    }

    private SafetyDeviceSyncEntity upsertDeviceSyncRow(String sn, String nickname, String status,
                                                       String msg, String zoneHash) {
        SafetyDeviceSyncEntity row = safetyDeviceSyncMapper.selectOne(new LambdaQueryWrapper<SafetyDeviceSyncEntity>()
                .eq(SafetyDeviceSyncEntity::getWorkspaceId, workspaceId())
                .eq(SafetyDeviceSyncEntity::getDeviceSn, sn)
                .last("LIMIT 1"));
        if (row == null) {
            row = new SafetyDeviceSyncEntity();
            row.setWorkspaceId(workspaceId());
            row.setDeviceSn(sn);
            row.setCreatedAt(LocalDateTime.now());
        }
        if (StringUtils.hasText(nickname)) {
            row.setNickname(nickname);
        } else if (!StringUtils.hasText(row.getNickname())) {
            row.setNickname(sn);
        }
        row.setSyncStatus(status);
        row.setSyncMsg(msg);
        if (zoneHash != null) {
            row.setZoneHash(zoneHash);
        }
        row.setUpdatedAt(LocalDateTime.now());
        if (row.getId() == null) {
            safetyDeviceSyncMapper.insert(row);
        } else {
            safetyDeviceSyncMapper.updateById(row);
        }
        return row;
    }

    private SafetyDeviceSyncVO toSyncVO(SafetyDeviceSyncEntity row) {
        return SafetyDeviceSyncVO.builder()
                .deviceSn(row.getDeviceSn())
                .nickname(row.getNickname())
                .syncStatus(row.getSyncStatus())
                .syncMsg(row.getSyncMsg())
                .zoneHash(row.getZoneHash())
                .updatedAt(row.getUpdatedAt() == null ? null : row.getUpdatedAt().format(FMT))
                .build();
    }

    private static String mapRemoteSyncStatus(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toLowerCase();
        if (value.isEmpty()) {
            return "unknown";
        }
        if (value.equals("waiting") || value.equals("wait_sync")) {
            return "wait_sync";
        }
        if (value.equals("synchronizing") || value.equals("syncing")) {
            return "synchronizing";
        }
        if (value.equals("synchronized") || value.equals("synced") || value.equals("success")) {
            return "synchronized";
        }
        if (value.equals("fail") || value.equals("failed") || value.equals("error")) {
            return "fail";
        }
        return "unknown";
    }

    private static String firstText(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && StringUtils.hasText(String.valueOf(v))) {
                return String.valueOf(v).trim();
            }
        }
        return null;
    }

    private void markDevicesNeedResync() {
        List<SafetyDeviceSyncEntity> rows = safetyDeviceSyncMapper.selectList(new LambdaQueryWrapper<SafetyDeviceSyncEntity>()
                .eq(SafetyDeviceSyncEntity::getWorkspaceId, workspaceId()));
        for (SafetyDeviceSyncEntity row : rows) {
            if ("synchronizing".equals(row.getSyncStatus())) {
                continue;
            }
            row.setSyncStatus("wait_sync");
            row.setSyncMsg("限飞区已变更，需重新同步");
            row.setUpdatedAt(LocalDateTime.now());
            safetyDeviceSyncMapper.updateById(row);
        }
    }

    private String computeZoneHash() {
        try {
            List<SafetyAreaVO> areas = safetyAreaMapper.selectList(new LambdaQueryWrapper<SafetyAreaEntity>()
                            .eq(SafetyAreaEntity::getWorkspaceId, workspaceId())
                            .orderByAsc(SafetyAreaEntity::getId))
                    .stream().map(this::toVO).toList();
            String payload = JSON.writeValueAsString(areas);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            log.warn("[safety] computeZoneHash failed: {}", e.getMessage());
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private void validateGeometry(SafetyAreaSaveRequest request) {
        if (request.getZoneType() == null || (request.getZoneType() != 3 && request.getZoneType() != 4)) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "限飞区类型非法");
        }
        List<SafetyCoordinateDTO> coords = request.getCoordinates();
        if (CollectionUtils.isEmpty(coords)) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "请绘制限飞区坐标");
        }
        for (SafetyCoordinateDTO c : coords) {
            if (c == null || c.getLongitude() == null || c.getLatitude() == null
                    || !Double.isFinite(c.getLongitude()) || !Double.isFinite(c.getLatitude())) {
                throw new BizException(ErrorCodes.GEOMETRY_INVALID, "坐标非法");
            }
        }
        if (request.getZoneType() == 3 && coords.size() < 3) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "多边形限飞区至少需要 3 个点");
        }
        if (request.getZoneType() == 4) {
            BigDecimal radius = request.getRadiusM() == null ? BigDecimal.valueOf(300) : request.getRadiusM();
            if (radius.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCodes.GEOMETRY_INVALID, "圆形半径必须大于 0");
            }
        }
    }

    private void applyRequest(SafetyAreaEntity entity, SafetyAreaSaveRequest request) {
        entity.setName(request.getName().trim());
        entity.setZoneType(request.getZoneType());
        entity.setColor(StringUtils.hasText(request.getColor()) ? request.getColor().trim() : "#E23C39");
        entity.setLineWidth(request.getLineWidth() == null || request.getLineWidth() <= 0 ? 4 : request.getLineWidth());
        entity.setClampToGround(Boolean.FALSE.equals(request.getClampToGround()) ? 0 : 1);
        if (request.getZoneType() == 4) {
            entity.setRadiusM(request.getRadiusM() == null ? BigDecimal.valueOf(300) : request.getRadiusM());
        } else {
            entity.setRadiusM(null);
        }
        entity.setContent(buildContentJson(entity, request.getCoordinates()));
    }

    private String buildContentJson(SafetyAreaEntity entity, List<SafetyCoordinateDTO> coordinates) {
        try {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("color", entity.getColor());
            properties.put("width", entity.getLineWidth());
            properties.put("clampToGround", entity.getClampToGround() == null || entity.getClampToGround() == 1);

            Map<String, Object> geometry = new LinkedHashMap<>();
            if (Objects.equals(entity.getZoneType(), 4)) {
                SafetyCoordinateDTO center = coordinates.get(0);
                geometry.put("type", "Circle");
                geometry.put("coordinates", List.of());
                geometry.put("center", List.of(center.getLongitude(), center.getLatitude()));
                geometry.put("radius", entity.getRadiusM());
            } else {
                List<List<Double>> ring = new ArrayList<>();
                for (SafetyCoordinateDTO c : coordinates) {
                    ring.add(List.of(c.getLongitude(), c.getLatitude()));
                }
                List<Double> first = ring.get(0);
                List<Double> last = ring.get(ring.size() - 1);
                if (!Objects.equals(first.get(0), last.get(0)) || !Objects.equals(first.get(1), last.get(1))) {
                    ring.add(List.of(first.get(0), first.get(1)));
                }
                geometry.put("type", "Polygon");
                geometry.put("coordinates", List.of(ring));
                geometry.put("center", List.of());
                geometry.put("radius", 0);
            }

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", properties);
            feature.put("geometry", geometry);
            return JSON.writeValueAsString(feature);
        } catch (Exception e) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "几何内容序列化失败");
        }
    }

    private SafetyAreaVO toVO(SafetyAreaEntity entity) {
        List<SafetyCoordinateDTO> coordinates = new ArrayList<>();
        BigDecimal radius = entity.getRadiusM();
        try {
            Map<String, Object> feature = JSON.readValue(entity.getContent(), new TypeReference<>() {});
            Object geometryObj = feature.get("geometry");
            if (geometryObj instanceof Map<?, ?> geometry) {
                String type = String.valueOf(geometry.get("type"));
                if ("Circle".equalsIgnoreCase(type)) {
                    Object center = geometry.get("center");
                    SafetyCoordinateDTO c = parsePoint(center);
                    if (c != null) {
                        coordinates.add(c);
                    }
                    Object r = geometry.get("radius");
                    if (r != null) {
                        radius = new BigDecimal(String.valueOf(r));
                    }
                } else {
                    Object coords = geometry.get("coordinates");
                    coordinates.addAll(extractPolygon(coords));
                }
            }
        } catch (Exception e) {
            log.warn("[safety] parse content id={} failed: {}", entity.getId(), e.getMessage());
        }
        return SafetyAreaVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .zoneType(entity.getZoneType())
                .coordinates(coordinates)
                .radiusM(radius)
                .color(entity.getColor())
                .lineWidth(entity.getLineWidth())
                .clampToGround(entity.getClampToGround() == null || entity.getClampToGround() == 1)
                .syncStatus(entity.getSyncStatus())
                .updatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(FMT))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<SafetyCoordinateDTO> extractPolygon(Object coordinates) {
        List<SafetyCoordinateDTO> out = new ArrayList<>();
        if (!(coordinates instanceof List<?> outer) || outer.isEmpty()) {
            return out;
        }
        Object first = outer.get(0);
        List<?> ring;
        if (first instanceof List<?> && !((List<?>) first).isEmpty() && ((List<?>) first).get(0) instanceof List<?>) {
            ring = (List<?>) first;
        } else if (first instanceof List<?> && !((List<?>) first).isEmpty() && ((List<?>) first).get(0) instanceof Number) {
            ring = outer;
        } else {
            return out;
        }
        for (Object p : ring) {
            SafetyCoordinateDTO c = parsePoint(p);
            if (c != null) {
                out.add(c);
            }
        }
        // 去掉闭合重复点
        if (out.size() >= 2) {
            SafetyCoordinateDTO a = out.get(0);
            SafetyCoordinateDTO b = out.get(out.size() - 1);
            if (Objects.equals(a.getLongitude(), b.getLongitude()) && Objects.equals(a.getLatitude(), b.getLatitude())) {
                out.remove(out.size() - 1);
            }
        }
        return out;
    }

    private SafetyCoordinateDTO parsePoint(Object point) {
        if (point instanceof List<?> arr && arr.size() >= 2) {
            SafetyCoordinateDTO c = new SafetyCoordinateDTO();
            c.setLongitude(Double.valueOf(String.valueOf(arr.get(0))));
            c.setLatitude(Double.valueOf(String.valueOf(arr.get(1))));
            return c;
        }
        if (point instanceof Map<?, ?> map) {
            Object lng = map.get("longitude");
            if (lng == null) lng = map.get("lng");
            Object lat = map.get("latitude");
            if (lat == null) lat = map.get("lat");
            if (lng != null && lat != null) {
                SafetyCoordinateDTO c = new SafetyCoordinateDTO();
                c.setLongitude(Double.valueOf(String.valueOf(lng)));
                c.setLatitude(Double.valueOf(String.valueOf(lat)));
                return c;
            }
        }
        return null;
    }

    private SafetyAreaEntity requireArea(Long id) {
        SafetyAreaEntity entity = safetyAreaMapper.selectById(id);
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.SAFETY_AREA_NOT_FOUND, "限飞区不存在");
        }
        return entity;
    }

    private Long workspaceId() {
        LoginUser user = UserContext.get();
        return user == null || user.getWorkspaceId() == null ? 1L : user.getWorkspaceId();
    }

    private Long userId() {
        LoginUser user = UserContext.get();
        return user == null ? null : user.getUserId();
    }
}
