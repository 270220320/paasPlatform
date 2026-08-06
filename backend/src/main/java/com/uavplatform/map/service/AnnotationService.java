package com.uavplatform.map.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.map.domain.AnnotationEntity;
import com.uavplatform.map.domain.AnnotationFolderEntity;
import com.uavplatform.map.domain.SyncJobEntity;
import com.uavplatform.map.domain.SyncJobItemEntity;
import com.uavplatform.map.dto.AnnotationCreateRequest;
import com.uavplatform.map.dto.AnnotationFolderCreateRequest;
import com.uavplatform.map.dto.AnnotationFolderVO;
import com.uavplatform.map.dto.AnnotationSyncRequest;
import com.uavplatform.map.dto.AnnotationUpdateRequest;
import com.uavplatform.map.dto.AnnotationVO;
import com.uavplatform.map.dto.MapTabMetaVO;
import com.uavplatform.map.dto.MapTabsMetaResponse;
import com.uavplatform.map.dto.SyncJobVO;
import com.uavplatform.map.mapper.AnnotationFolderMapper;
import com.uavplatform.map.mapper.AnnotationMapper;
import com.uavplatform.map.mapper.SyncJobItemMapper;
import com.uavplatform.map.mapper.SyncJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnotationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> GEOM_TYPES = Set.of("point", "line", "polygon", "circle");
    private static final Map<String, String> GEOJSON_TYPE_MAP = Map.of(
            "point", "Point",
            "line", "LineString",
            "polygon", "Polygon",
            "circle", "Point");

    private final AnnotationMapper annotationMapper;
    private final AnnotationFolderMapper annotationFolderMapper;
    private final SyncJobMapper syncJobMapper;
    private final SyncJobItemMapper syncJobItemMapper;
    private final DeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;

    public List<AnnotationVO> list(Long folderId, String purpose, Boolean visible) {
        PermissionChecker.require("map:read");
        LambdaQueryWrapper<AnnotationEntity> qw = new LambdaQueryWrapper<AnnotationEntity>()
                .eq(AnnotationEntity::getWorkspaceId, workspaceId());
        if (folderId != null) {
            qw.eq(AnnotationEntity::getFolderId, folderId);
        }
        if (StringUtils.hasText(purpose)) {
            qw.eq(AnnotationEntity::getPurpose, purpose);
        }
        if (visible != null) {
            qw.eq(AnnotationEntity::getVisible, visible ? 1 : 0);
        }
        qw.orderByDesc(AnnotationEntity::getUpdatedAt);
        return annotationMapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public AnnotationVO create(AnnotationCreateRequest request) {
        PermissionChecker.require("map:write");
        validateGeometry(request.getGeomType(), request.getGeometry());
        if (request.getFolderId() != null) {
            ensureFolderWritable(request.getFolderId());
        }
        AnnotationEntity entity = new AnnotationEntity();
        entity.setWorkspaceId(workspaceId());
        entity.setFolderId(request.getFolderId());
        entity.setName(request.getName().trim());
        entity.setGeomType(request.getGeomType());
        entity.setPurpose(request.getPurpose());
        entity.setGeometry(request.getGeometry());
        entity.setStyle(request.getStyle());
        entity.setVisible(request.getVisible() == null || request.getVisible() ? 1 : 0);
        entity.setSyncStatus("pending");
        entity.setUpdatedBy(currentUserId());
        annotationMapper.insert(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public AnnotationVO update(Long id, AnnotationUpdateRequest request) {
        PermissionChecker.require("map:write");
        AnnotationEntity entity = requireAnnotation(id);
        if (entity.getFolderId() != null) {
            ensureFolderWritable(entity.getFolderId());
        }
        if (request.getFolderId() != null) {
            ensureFolderWritable(request.getFolderId());
            entity.setFolderId(request.getFolderId());
        }
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getGeomType()) || StringUtils.hasText(request.getGeometry())) {
            String geomType = StringUtils.hasText(request.getGeomType()) ? request.getGeomType() : entity.getGeomType();
            String geometry = StringUtils.hasText(request.getGeometry()) ? request.getGeometry() : entity.getGeometry();
            validateGeometry(geomType, geometry);
            entity.setGeomType(geomType);
            entity.setGeometry(geometry);
        }
        if (request.getPurpose() != null) {
            entity.setPurpose(request.getPurpose());
        }
        if (request.getStyle() != null) {
            entity.setStyle(request.getStyle());
        }
        if (request.getVisible() != null) {
            entity.setVisible(request.getVisible() ? 1 : 0);
        }
        entity.setSyncStatus("pending");
        entity.setUpdatedBy(currentUserId());
        annotationMapper.updateById(entity);
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("map:write");
        AnnotationEntity entity = requireAnnotation(id);
        if (entity.getFolderId() != null) {
            ensureFolderWritable(entity.getFolderId());
        }
        annotationMapper.deleteById(id);
    }

    public List<AnnotationFolderVO> listFolders() {
        PermissionChecker.require("map:read");
        return annotationFolderMapper.selectList(new LambdaQueryWrapper<AnnotationFolderEntity>()
                        .eq(AnnotationFolderEntity::getWorkspaceId, workspaceId())
                        .orderByAsc(AnnotationFolderEntity::getName))
                .stream().map(this::toFolderVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public IdVO createFolder(AnnotationFolderCreateRequest request) {
        PermissionChecker.require("map:write");
        AnnotationFolderEntity folder = new AnnotationFolderEntity();
        folder.setWorkspaceId(workspaceId());
        folder.setName(request.getName().trim());
        folder.setLocked(0);
        annotationFolderMapper.insert(folder);
        return new IdVO(folder.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncJobVO sync(AnnotationSyncRequest request) {
        PermissionChecker.require("map:write");
        Long wsId = workspaceId();
        Long running = syncJobMapper.selectCount(new LambdaQueryWrapper<SyncJobEntity>()
                .eq(SyncJobEntity::getWorkspaceId, wsId)
                .eq(SyncJobEntity::getStatus, "running"));
        if (running != null && running > 0) {
            throw new BizException(ErrorCodes.SYNC_RUNNING, "同步任务进行中");
        }

        List<DeviceEntity> devices = resolveDevices(request == null ? null : request.getDeviceIds());
        if (devices.isEmpty()) {
            throw new BizException(ErrorCodes.NO_DEVICE_TO_SYNC, "无设备可同步");
        }

        List<AnnotationEntity> annotations = resolveAnnotations(request == null ? null : request.getAnnotationIds());
        if (annotations.isEmpty()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "无标注可同步");
        }

        SyncJobEntity job = new SyncJobEntity();
        job.setWorkspaceId(wsId);
        job.setStatus("running");
        job.setStartedAt(LocalDateTime.now());
        job.setCreatedBy(currentUserId());
        syncJobMapper.insert(job);

        int success = 0;
        for (AnnotationEntity ann : annotations) {
            for (DeviceEntity device : devices) {
                SyncJobItemEntity item = new SyncJobItemEntity();
                item.setSyncJobId(job.getId());
                item.setAnnotationId(ann.getId());
                item.setDeviceId(device.getId());
                item.setResult("success");
                syncJobItemMapper.insert(item);
                success++;
            }
            ann.setSyncStatus("synced");
            annotationMapper.updateById(ann);
        }

        job.setStatus("success");
        job.setMessage("同步完成");
        job.setFinishedAt(LocalDateTime.now());
        syncJobMapper.updateById(job);

        return SyncJobVO.builder()
                .syncJobId(job.getId())
                .status(job.getStatus())
                .message(job.getMessage())
                .successCount(success)
                .failedCount(0)
                .totalCount(success)
                .build();
    }

    public SyncJobVO getSyncJob(Long jobId) {
        PermissionChecker.require("map:read");
        SyncJobEntity job = syncJobMapper.selectById(jobId);
        if (job == null || !wsId(job.getWorkspaceId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "同步任务不存在");
        }
        List<SyncJobItemEntity> items = syncJobItemMapper.selectList(
                new LambdaQueryWrapper<SyncJobItemEntity>().eq(SyncJobItemEntity::getSyncJobId, jobId));
        int success = (int) items.stream().filter(i -> "success".equals(i.getResult())).count();
        int failed = (int) items.stream().filter(i -> "failed".equals(i.getResult())).count();
        return SyncJobVO.builder()
                .syncJobId(job.getId())
                .status(job.getStatus())
                .message(job.getMessage())
                .successCount(success)
                .failedCount(failed)
                .totalCount(items.size())
                .build();
    }

    public MapTabsMetaResponse tabsMeta() {
        PermissionChecker.require("map:read");
        MapTabsMetaResponse resp = new MapTabsMetaResponse();
        List<MapTabMetaVO> tabs = new ArrayList<>();
        tabs.add(MapTabMetaVO.builder().key("annotation").enabled(true).build());
        tabs.add(MapTabMetaVO.builder().key("elevation").enabled(false).reason("P1 upcoming").build());
        tabs.add(MapTabMetaVO.builder().key("wayline").enabled(true).route("workspace.waylines").build());
        tabs.add(MapTabMetaVO.builder().key("safety").enabled(false).reason("P1 upcoming").build());
        resp.setTabs(tabs);
        return resp;
    }

    /** 校验 GeoJSON 类型与 geomType 一致。 */
    public void validateGeometry(String geomType, String geometry) {
        if (!GEOM_TYPES.contains(geomType)) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "几何非法");
        }
        if (!StringUtils.hasText(geometry)) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "几何非法");
        }
        try {
            JsonNode node = objectMapper.readTree(geometry);
            String type = node.has("type") ? node.get("type").asText() : null;
            String expected = GEOJSON_TYPE_MAP.get(geomType);
            if (expected == null || !expected.equals(type)) {
                throw new BizException(ErrorCodes.GEOMETRY_INVALID, "几何非法");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCodes.GEOMETRY_INVALID, "几何非法");
        }
    }

    private List<DeviceEntity> resolveDevices(List<Long> deviceIds) {
        LambdaQueryWrapper<DeviceEntity> qw = new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getWorkspaceId, workspaceId())
                .in(DeviceEntity::getDeviceType, "dock", "aircraft");
        if (deviceIds != null && !deviceIds.isEmpty()) {
            qw.in(DeviceEntity::getId, deviceIds);
        }
        return deviceMapper.selectList(qw);
    }

    private List<AnnotationEntity> resolveAnnotations(List<Long> annotationIds) {
        LambdaQueryWrapper<AnnotationEntity> qw = new LambdaQueryWrapper<AnnotationEntity>()
                .eq(AnnotationEntity::getWorkspaceId, workspaceId())
                .eq(AnnotationEntity::getVisible, 1);
        if (annotationIds != null && !annotationIds.isEmpty()) {
            qw.in(AnnotationEntity::getId, annotationIds);
        } else {
            qw.in(AnnotationEntity::getSyncStatus, "pending", "failed");
        }
        return annotationMapper.selectList(qw);
    }

    private AnnotationEntity requireAnnotation(Long id) {
        AnnotationEntity entity = annotationMapper.selectById(id);
        if (entity == null || !wsId(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.ANNOTATION_NOT_FOUND, "标注不存在");
        }
        return entity;
    }

    private void ensureFolderWritable(Long folderId) {
        AnnotationFolderEntity folder = annotationFolderMapper.selectById(folderId);
        if (folder == null || !wsId(folder.getWorkspaceId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "文件夹不存在");
        }
        if (folder.getLocked() != null && folder.getLocked() == 1) {
            throw new BizException(ErrorCodes.FOLDER_LOCKED, "文件夹已锁定");
        }
    }

    private AnnotationVO toVO(AnnotationEntity e) {
        return AnnotationVO.builder()
                .id(e.getId())
                .name(e.getName())
                .geomType(e.getGeomType())
                .purpose(e.getPurpose())
                .geometry(e.getGeometry())
                .style(e.getStyle())
                .visible(e.getVisible() != null && e.getVisible() == 1)
                .syncStatus(e.getSyncStatus())
                .folderId(e.getFolderId())
                .updatedAt(e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(FMT))
                .build();
    }

    private AnnotationFolderVO toFolderVO(AnnotationFolderEntity f) {
        return AnnotationFolderVO.builder()
                .id(f.getId())
                .name(f.getName())
                .locked(f.getLocked() != null && f.getLocked() == 1)
                .createdAt(f.getCreatedAt() == null ? null : f.getCreatedAt().format(FMT))
                .build();
    }

    private Long workspaceId() {
        LoginUser u = UserContext.get();
        return u == null || u.getWorkspaceId() == null ? 1L : u.getWorkspaceId();
    }

    private Long currentUserId() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUserId();
    }

    private boolean wsId(Long ws) {
        return workspaceId().equals(ws);
    }
}
