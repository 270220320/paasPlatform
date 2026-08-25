package com.uavplatform.wayline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.wayline.domain.WaylineContentEntity;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.domain.WaylineFolderEntity;
import com.uavplatform.wayline.domain.WaylineSnapshotEntity;
import com.uavplatform.wayline.dto.WaylineContentVO;
import com.uavplatform.wayline.dto.WaylineCreateRequest;
import com.uavplatform.wayline.dto.WaylineDetailVO;
import com.uavplatform.wayline.dto.WaylineFolderCreateRequest;
import com.uavplatform.wayline.dto.WaylineFolderVO;
import com.uavplatform.wayline.dto.WaylineListVO;
import com.uavplatform.wayline.dto.WaylineUpdateRequest;
import com.uavplatform.wayline.mapper.WaylineContentMapper;
import com.uavplatform.wayline.mapper.WaylineFolderMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import com.uavplatform.wayline.mapper.WaylineSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaylineService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_FOLDER_NAME = "默认文件夹";
    private static final Set<String> ALLOWED_TYPES = Set.of("waypoint", "grid_2d");
    private static final Set<String> BLOCKING_MISSION_STATUS = Set.of("pending", "running", "paused");

    private final WaylineMapper waylineMapper;
    private final WaylineContentMapper waylineContentMapper;
    private final WaylineFolderMapper waylineFolderMapper;
    private final WaylineSnapshotMapper waylineSnapshotMapper;
    private final MissionPlanMapper missionPlanMapper;
    private final Fh2SdkClient fh2SdkClient;

    public List<WaylineFolderVO> listFolders() {
        PermissionChecker.require("wayline:read");
        // 一次查出各文件夹航线数，避免逐文件夹 count 的 N+1 查询
        Map<Long, Long> waylineCounts = waylineMapper.selectList(new LambdaQueryWrapper<WaylineEntity>()
                        .eq(WaylineEntity::getWorkspaceId, workspaceId())
                        .select(WaylineEntity::getFolderId))
                .stream()
                .filter(w -> w.getFolderId() != null)
                .collect(Collectors.groupingBy(WaylineEntity::getFolderId, Collectors.counting()));
        return waylineFolderMapper.selectList(new LambdaQueryWrapper<WaylineFolderEntity>()
                        .eq(WaylineFolderEntity::getWorkspaceId, workspaceId())
                        .orderByAsc(WaylineFolderEntity::getName))
                .stream().map(f -> toFolderVO(f, waylineCounts.getOrDefault(f.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public IdVO createFolder(WaylineFolderCreateRequest request) {
        PermissionChecker.require("wayline:write");
        WaylineFolderEntity folder = new WaylineFolderEntity();
        folder.setWorkspaceId(workspaceId());
        folder.setName(request.getName().trim());
        folder.setParentId(request.getParentId());
        waylineFolderMapper.insert(folder);
        return new IdVO(folder.getId());
    }

    /**
     * 删除文件夹（含最后一个）：有航线的文件夹不可删（需先删除或移出航线）；
     * 删除后不自动重建任何文件夹，子文件夹挂回根级。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long id) {
        PermissionChecker.require("wayline:write");
        WaylineFolderEntity folder = waylineFolderMapper.selectById(id);
        if (folder == null || !workspaceId().equals(folder.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WAYLINE_FOLDER_NOT_FOUND, "文件夹不存在");
        }
        Long waylineCount = waylineMapper.selectCount(new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getFolderId, id)
                .eq(WaylineEntity::getWorkspaceId, workspaceId()));
        if (waylineCount != null && waylineCount > 0) {
            throw new BizException(ErrorCodes.WAYLINE_FOLDER_NOT_EMPTY,
                    "文件夹下有 " + waylineCount + " 条航线，请先删除或移出后再删除文件夹");
        }
        // 子文件夹挂回根级，避免悬空引用（set(null) 才能真正置空，entity patch 会跳过 null 字段）
        waylineFolderMapper.update(null, new LambdaUpdateWrapper<WaylineFolderEntity>()
                .set(WaylineFolderEntity::getParentId, null)
                .eq(WaylineFolderEntity::getParentId, id)
                .eq(WaylineFolderEntity::getWorkspaceId, workspaceId()));
        waylineFolderMapper.deleteById(id);
    }

    /**
     * 航线列表读本地库（按文件夹 / 名称 / 状态过滤），未删除记录；司空侧由编辑器 paas.js 保存。
     */
    public PageResult<WaylineListVO> page(Long folderId, String aircraftModel, String status,
                                          String keyword, long page, long pageSize) {
        PermissionChecker.require("wayline:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        LambdaQueryWrapper<WaylineEntity> qw = new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getWorkspaceId, workspaceId());
        if (folderId != null) {
            qw.eq(WaylineEntity::getFolderId, folderId);
        }
        if (StringUtils.hasText(aircraftModel)) {
            qw.eq(WaylineEntity::getAircraftModel, aircraftModel.trim());
        }
        if (StringUtils.hasText(status)) {
            qw.eq(WaylineEntity::getStatus, status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(WaylineEntity::getName, keyword.trim());
        }
        qw.orderByDesc(WaylineEntity::getUpdatedAt);
        Page<WaylineEntity> result = waylineMapper.selectPage(new Page<>(p, size), qw);
        List<WaylineListVO> list = result.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    /** 仅在创建航线等必须归属时兜底：取默认文件夹，无则取最早文件夹，都没有则新建默认文件夹。 */
    @Transactional(rollbackFor = Exception.class)
    public Long ensureDefaultFolder() {
        Long ws = workspaceId();
        WaylineFolderEntity exist = waylineFolderMapper.selectOne(new LambdaQueryWrapper<WaylineFolderEntity>()
                .eq(WaylineFolderEntity::getWorkspaceId, ws)
                .eq(WaylineFolderEntity::getName, DEFAULT_FOLDER_NAME)
                .last("LIMIT 1"));
        Long folderId;
        if (exist != null) {
            folderId = exist.getId();
        } else {
            Long count = waylineFolderMapper.selectCount(new LambdaQueryWrapper<WaylineFolderEntity>()
                    .eq(WaylineFolderEntity::getWorkspaceId, ws));
            if (count != null && count > 0) {
                WaylineFolderEntity first = waylineFolderMapper.selectOne(new LambdaQueryWrapper<WaylineFolderEntity>()
                        .eq(WaylineFolderEntity::getWorkspaceId, ws)
                        .orderByAsc(WaylineFolderEntity::getId)
                        .last("LIMIT 1"));
                folderId = first == null ? null : first.getId();
            } else {
                WaylineFolderEntity folder = new WaylineFolderEntity();
                folder.setWorkspaceId(ws);
                folder.setName(DEFAULT_FOLDER_NAME);
                waylineFolderMapper.insert(folder);
                folderId = folder.getId();
            }
        }
        if (folderId != null) {
            WaylineEntity patch = new WaylineEntity();
            patch.setFolderId(folderId);
            waylineMapper.update(patch, new LambdaQueryWrapper<WaylineEntity>()
                    .eq(WaylineEntity::getWorkspaceId, ws)
                    .isNull(WaylineEntity::getFolderId));
        }
        return folderId;
    }

    public WaylineDetailVO detail(Long id) {
        PermissionChecker.require("wayline:read");
        WaylineEntity entity = requireWayline(id);
        WaylineContentEntity content = waylineContentMapper.selectById(id);
        return toDetailVO(entity, content);
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineDetailVO create(WaylineCreateRequest request) {
        PermissionChecker.require("wayline:write");
        validateName(request.getName());
        validateType(request.getWaylineType());

        WaylineEntity entity = new WaylineEntity();
        entity.setWorkspaceId(workspaceId());
        entity.setFolderId(request.getFolderId() != null ? request.getFolderId() : ensureDefaultFolder());
        entity.setName(request.getName().trim());
        entity.setAircraftModel(request.getAircraftModel().trim());
        entity.setWaylineType(request.getWaylineType());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "draft");
        entity.setUpdatedBy(currentUserId());
        waylineMapper.insert(entity);

        WaylineContentEntity content = new WaylineContentEntity();
        content.setWaylineId(entity.getId());
        content.setStructuredParams(request.getStructuredParams());
        content.setChecksum(computeChecksum(request.getStructuredParams()));
        waylineContentMapper.insert(content);
        return toDetailVO(entity, content);
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineDetailVO update(Long id, WaylineUpdateRequest request) {
        PermissionChecker.require("wayline:write");
        WaylineEntity entity = requireWayline(id);
        if (StringUtils.hasText(request.getName())) {
            validateName(request.getName());
            entity.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            entity.setStatus(request.getStatus());
        }
        if (request.getFolderId() != null) {
            entity.setFolderId(request.getFolderId());
        }
        entity.setUpdatedBy(currentUserId());
        waylineMapper.updateById(entity);

        WaylineContentEntity content = waylineContentMapper.selectById(id);
        if (content == null) {
            content = new WaylineContentEntity();
            content.setWaylineId(id);
        }
        if (request.getStructuredParams() != null) {
            content.setStructuredParams(request.getStructuredParams());
            content.setChecksum(computeChecksum(request.getStructuredParams()));
        }
        if (waylineContentMapper.selectById(id) == null) {
            waylineContentMapper.insert(content);
        } else {
            waylineContentMapper.updateById(content);
        }
        return toDetailVO(entity, content);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("wayline:write");
        WaylineEntity entity = waylineMapper.selectById(id);
        if (entity != null && workspaceId().equals(entity.getWorkspaceId())
                && StringUtils.hasText(entity.getFh2WaylineUuid())) {
            deleteRemoteAndLocal(entity.getFh2WaylineUuid(), entity);
            return;
        }
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WAYLINE_NOT_FOUND, "航线不存在");
        }
        Long inUse = missionPlanMapper.selectCount(new LambdaQueryWrapper<MissionPlanEntity>()
                .eq(MissionPlanEntity::getWaylineId, id)
                .in(MissionPlanEntity::getStatus, BLOCKING_MISSION_STATUS));
        if (inUse != null && inUse > 0) {
            throw new BizException(ErrorCodes.WAYLINE_IN_USE, "航线被任务引用，禁止删除");
        }
        waylineContentMapper.deleteById(id);
        waylineMapper.deleteById(id);
    }

    /** 按司空航线 UUID 删除远端，并清理本地镜像（如有）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUuid(String waylineUuid) {
        PermissionChecker.require("wayline:write");
        if (!StringUtils.hasText(waylineUuid)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "waylineUuid 必填");
        }
        WaylineEntity local = waylineMapper.selectOne(new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getFh2WaylineUuid, waylineUuid.trim())
                .last("LIMIT 1"));
        deleteRemoteAndLocal(waylineUuid.trim(), local);
    }

    private void deleteRemoteAndLocal(String waylineUuid, WaylineEntity local) {
        if (local != null) {
            Long inUse = missionPlanMapper.selectCount(new LambdaQueryWrapper<MissionPlanEntity>()
                    .eq(MissionPlanEntity::getWaylineId, local.getId())
                    .in(MissionPlanEntity::getStatus, BLOCKING_MISSION_STATUS));
            if (inUse != null && inUse > 0) {
                throw new BizException(ErrorCodes.WAYLINE_IN_USE, "航线被任务引用，禁止删除");
            }
        }
        // SDK 未就绪（如私有化未配置 userToken/bindingUuid）：当前部署不连司空，降级为仅删本地镜像
        if (!fh2SdkClient.available()) {
            if (local == null) {
                throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                        "无法连接航线服务，且本地无该航线镜像：" + waylineUuid);
            }
            log.warn("[wayline] FH2 SDK 未就绪，仅删除本地镜像 uuid={}", waylineUuid);
            deleteLocalMirror(local);
            return;
        }
        try {
            fh2SdkClient.deleteWaylines(List.of(waylineUuid));
        } catch (Exception e) {
            log.error("[wayline] FH2 delete failed uuid={}: {}", waylineUuid, e.getMessage(), e);
            // 远端航线已不存在（旧环境同步过来的残留数据）时放行本地清理
            if (local != null && isRemoteWaylineMissing(e)) {
                log.warn("[wayline] 远端航线不存在，清理本地镜像 uuid={}", waylineUuid);
                deleteLocalMirror(local);
                return;
            }
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "删除航线失败：" + e.getMessage());
        }
        if (local != null) {
            deleteLocalMirror(local);
        }
    }

    private void deleteLocalMirror(WaylineEntity local) {
        waylineContentMapper.deleteById(local.getId());
        waylineMapper.deleteById(local.getId());
    }

    /** 宽松识别司空侧“航线不存在”类错误，避免残留镜像无法删除 */
    private boolean isRemoteWaylineMissing(Exception e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("not exist") || msg.contains("not found") || msg.contains("不存在");
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineDetailVO importFile(MultipartFile file, Long folderId, String aircraftModel) {
        PermissionChecker.require("wayline:write");
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "KMZ 解析失败");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "KMZ 解析失败");
        }
        String storageKey = "waylines/import/" + filename;
        String structuredParams = "{\"imported\":true,\"file\":\"" + filename.replace("\"", "") + "\"}";

        WaylineCreateRequest req = new WaylineCreateRequest();
        req.setName(filename.length() > 60 ? filename.substring(0, 60) : filename);
        req.setAircraftModel(StringUtils.hasText(aircraftModel) ? aircraftModel : "M350 RTK");
        req.setWaylineType("waypoint");
        req.setFolderId(folderId);
        req.setStructuredParams(structuredParams);
        req.setStatus("draft");

        WaylineDetailVO created = create(req);
        WaylineContentEntity content = waylineContentMapper.selectById(created.getId());
        content.setStorageKey(storageKey);
        waylineContentMapper.updateById(content);
        created.getContent().setStorageKey(storageKey);
        return created;
    }

    public byte[] export(Long id) {
        PermissionChecker.require("wayline:read");
        WaylineEntity entity = requireWayline(id);
        WaylineContentEntity content = waylineContentMapper.selectById(id);
        String data = content != null && StringUtils.hasText(content.getStructuredParams())
                ? content.getStructuredParams()
                : (content != null && StringUtils.hasText(content.getStorageKey())
                ? content.getStorageKey() : entity.getName());
        return data.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineSnapshotEntity createSnapshot(WaylineEntity wayline, WaylineContentEntity content) {
        WaylineSnapshotEntity snapshot = new WaylineSnapshotEntity();
        snapshot.setSourceWaylineId(wayline.getId());
        snapshot.setName(StringUtils.hasText(wayline.getName()) ? wayline.getName() : "未命名航线");
        snapshot.setAircraftModel(StringUtils.hasText(wayline.getAircraftModel()) ? wayline.getAircraftModel() : "unknown");
        snapshot.setWaylineType(StringUtils.hasText(wayline.getWaylineType()) ? wayline.getWaylineType() : "waypoint");
        if (content != null) {
            snapshot.setStorageKey(content.getStorageKey());
            snapshot.setStructuredParams(content.getStructuredParams());
            snapshot.setChecksum(content.getChecksum());
        }
        waylineSnapshotMapper.insert(snapshot);
        return snapshot;
    }

    public WaylineEntity requirePublishedWayline(Long id) {
        WaylineEntity entity = requireWayline(id);
        // 本地镜像可能尚未标 published，有司空 UUID 的航线也允许建计划
        if (!"published".equals(entity.getStatus()) && !StringUtils.hasText(entity.getFh2WaylineUuid())) {
            throw new BizException(ErrorCodes.WAYLINE_UNAVAILABLE, "航线不可用");
        }
        return entity;
    }

    public WaylineEntity requireWayline(Long id) {
        WaylineEntity entity = waylineMapper.selectById(id);
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WAYLINE_NOT_FOUND, "航线不存在");
        }
        return entity;
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name) || name.trim().length() > 60) {
            throw new BizException(ErrorCodes.WAYLINE_NAME_INVALID, "航线名称非法");
        }
    }

    private void validateType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BizException(ErrorCodes.WAYLINE_TYPE_UNSUPPORTED, "不支持的航线类型");
        }
    }

    private String computeChecksum(String structuredParams) {
        if (!StringUtils.hasText(structuredParams)) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(structuredParams.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return null;
        }
    }

    private WaylineFolderVO toFolderVO(WaylineFolderEntity f, long waylineCount) {
        return WaylineFolderVO.builder()
                .id(f.getId())
                .name(f.getName())
                .parentId(f.getParentId())
                .createdAt(f.getCreatedAt() == null ? null : f.getCreatedAt().format(FMT))
                .waylineCount(waylineCount)
                .build();
    }

    private WaylineListVO toListVO(WaylineEntity e) {
        return WaylineListVO.builder()
                .id(e.getId())
                .name(e.getName())
                .aircraftModel(e.getAircraftModel())
                .waylineType(e.getWaylineType())
                .status(e.getStatus())
                .fh2WaylineUuid(e.getFh2WaylineUuid())
                .source(e.getSource())
                .folderId(e.getFolderId())
                .updatedAt(e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(FMT))
                .build();
    }

    private WaylineDetailVO toDetailVO(WaylineEntity e, WaylineContentEntity c) {
        WaylineContentVO contentVO = null;
        if (c != null) {
            contentVO = WaylineContentVO.builder()
                    .storageKey(c.getStorageKey())
                    .structuredParams(c.getStructuredParams())
                    .checksum(c.getChecksum())
                    .build();
        }
        return WaylineDetailVO.builder()
                .id(e.getId())
                .name(e.getName())
                .aircraftModel(e.getAircraftModel())
                .waylineType(e.getWaylineType())
                .status(e.getStatus())
                .folderId(e.getFolderId())
                .updatedAt(e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(FMT))
                .content(contentVO)
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
}
