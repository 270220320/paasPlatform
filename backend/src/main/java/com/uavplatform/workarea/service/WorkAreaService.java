package com.uavplatform.workarea.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.safety.dto.SafetyCoordinateDTO;
import com.uavplatform.workarea.domain.WorkAreaEntity;
import com.uavplatform.workarea.dto.WorkAreaSaveRequest;
import com.uavplatform.workarea.dto.WorkAreaVO;
import com.uavplatform.workarea.mapper.WorkAreaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkAreaService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_COLOR = "#2EB85C";

    private final WorkAreaMapper workAreaMapper;

    public List<WorkAreaVO> listAreas() {
        PermissionChecker.requireAny("map:read", "dashboard:read");
        return workAreaMapper.selectList(new LambdaQueryWrapper<WorkAreaEntity>()
                        .eq(WorkAreaEntity::getWorkspaceId, workspaceId())
                        .orderByDesc(WorkAreaEntity::getUpdatedAt)
                        .orderByDesc(WorkAreaEntity::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkAreaVO create(WorkAreaSaveRequest request) {
        PermissionChecker.require("map:write");
        validateGeometry(request);
        WorkAreaEntity entity = new WorkAreaEntity();
        entity.setWorkspaceId(workspaceId());
        applyRequest(entity, request);
        entity.setStatus("active");
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        workAreaMapper.insert(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkAreaVO update(Long id, WorkAreaSaveRequest request) {
        PermissionChecker.require("map:write");
        WorkAreaEntity entity = requireArea(id);
        validateGeometry(request);
        applyRequest(entity, request);
        entity.setUpdatedBy(userId());
        workAreaMapper.updateById(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("map:write");
        WorkAreaEntity entity = requireArea(id);
        workAreaMapper.deleteById(entity.getId());
    }

    private void validateGeometry(WorkAreaSaveRequest request) {
        if (request.getZoneType() == null || (request.getZoneType() != 3 && request.getZoneType() != 4)) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "作业区类型非法");
        }
        List<SafetyCoordinateDTO> coords = request.getCoordinates();
        if (CollectionUtils.isEmpty(coords)) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "请绘制作业区坐标");
        }
        for (SafetyCoordinateDTO c : coords) {
            if (c == null || c.getLongitude() == null || c.getLatitude() == null
                    || !Double.isFinite(c.getLongitude()) || !Double.isFinite(c.getLatitude())) {
                throw new BizException(ErrorCodes.GEOMETRY_INVALID, "坐标非法");
            }
        }
        if (request.getZoneType() == 3 && coords.size() < 3) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "多边形作业区至少需要 3 个点");
        }
        if (request.getZoneType() == 4) {
            BigDecimal radius = request.getRadiusM() == null ? BigDecimal.valueOf(300) : request.getRadiusM();
            if (radius.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCodes.GEOMETRY_INVALID, "圆形半径必须大于 0");
            }
        }
    }

    private void applyRequest(WorkAreaEntity entity, WorkAreaSaveRequest request) {
        entity.setName(request.getName().trim());
        entity.setZoneType(request.getZoneType());
        entity.setColor(StringUtils.hasText(request.getColor()) ? request.getColor().trim() : DEFAULT_COLOR);
        entity.setLineWidth(request.getLineWidth() == null || request.getLineWidth() <= 0 ? 4 : request.getLineWidth());
        entity.setClampToGround(Boolean.FALSE.equals(request.getClampToGround()) ? 0 : 1);
        entity.setMinAltitude(request.getMinAltitude());
        entity.setMaxAltitude(request.getMaxAltitude());
        entity.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        if (request.getZoneType() == 4) {
            entity.setRadiusM(request.getRadiusM() == null ? BigDecimal.valueOf(300) : request.getRadiusM());
        } else {
            entity.setRadiusM(null);
        }
        entity.setContent(buildContentJson(entity, request.getCoordinates()));
    }

    private String buildContentJson(WorkAreaEntity entity, List<SafetyCoordinateDTO> coordinates) {
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

    private WorkAreaVO toVO(WorkAreaEntity entity) {
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
            log.warn("[workarea] parse content id={} failed: {}", entity.getId(), e.getMessage());
        }
        return WorkAreaVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .zoneType(entity.getZoneType())
                .coordinates(coordinates)
                .radiusM(radius)
                .color(entity.getColor())
                .lineWidth(entity.getLineWidth())
                .clampToGround(entity.getClampToGround() == null || entity.getClampToGround() == 1)
                .minAltitude(entity.getMinAltitude())
                .maxAltitude(entity.getMaxAltitude())
                .description(entity.getDescription())
                .status(entity.getStatus())
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

    private WorkAreaEntity requireArea(Long id) {
        WorkAreaEntity entity = workAreaMapper.selectById(id);
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WORK_AREA_NOT_FOUND, "作业区不存在");
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
