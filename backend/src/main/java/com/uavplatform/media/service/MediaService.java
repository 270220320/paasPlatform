package com.uavplatform.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.analytics.domain.WorkspaceQuotaEntity;
import com.uavplatform.analytics.mapper.WorkspaceQuotaMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.dto.Fh2MediaFileVO;
import com.uavplatform.fh2.dto.Fh2MediaPageVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.media.domain.MediaAssetEntity;
import com.uavplatform.media.domain.MediaFolderEntity;
import com.uavplatform.media.domain.MediaTagEntity;
import com.uavplatform.media.domain.ModelAssetEntity;
import com.uavplatform.media.domain.ReconstructJobEntity;
import com.uavplatform.media.dto.DownloadUrlVO;
import com.uavplatform.media.dto.LibraryFeaturesVO;
import com.uavplatform.media.dto.MediaAssetVO;
import com.uavplatform.media.dto.MediaConfirmRequest;
import com.uavplatform.media.dto.MediaFolderCreateRequest;
import com.uavplatform.media.dto.MediaFolderVO;
import com.uavplatform.media.dto.ModelAssetVO;
import com.uavplatform.media.dto.QuotaVO;
import com.uavplatform.media.dto.ReconstructJobCreateRequest;
import com.uavplatform.media.dto.ReconstructJobVO;
import com.uavplatform.media.dto.TrashItemVO;
import com.uavplatform.media.dto.UploadCredentialRequest;
import com.uavplatform.media.dto.UploadCredentialVO;
import com.uavplatform.media.mapper.MediaAssetMapper;
import com.uavplatform.media.mapper.MediaFolderMapper;
import com.uavplatform.media.mapper.MediaTagMapper;
import com.uavplatform.media.mapper.ModelAssetMapper;
import com.uavplatform.media.mapper.ReconstructJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MediaAssetMapper mediaAssetMapper;
    private final MediaFolderMapper mediaFolderMapper;
    private final MediaTagMapper mediaTagMapper;
    private final ModelAssetMapper modelAssetMapper;
    private final ReconstructJobMapper reconstructJobMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final Fh2SdkClient fh2SdkClient;

    public UploadCredentialVO uploadCredential(UploadCredentialRequest request) {
        PermissionChecker.require("library:write");
        WorkspaceQuotaEntity quota = requireQuota();
        if (quota.getStorageUsedBytes() + request.getSizeBytes() > quota.getStorageTotalBytes()) {
            throw new BizException(ErrorCodes.QUOTA_EXCEEDED, "存储配额不足");
        }
        String storageKey = "media/" + workspaceId() + "/" + UUID.randomUUID() + "/" + request.getFileName();
        LocalDateTime expire = LocalDateTime.now().plusHours(1);
        return UploadCredentialVO.builder()
                .uploadUrl("https://oss-stub.local/upload/" + storageKey)
                .storageKey(storageKey)
                .headers(Map.of("Content-Type", "application/octet-stream"))
                .expireAt(expire.format(FMT))
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public MediaAssetVO confirmMedia(MediaConfirmRequest request) {
        PermissionChecker.require("library:write");
        WorkspaceQuotaEntity quota = requireQuota();
        if (quota.getStorageUsedBytes() + request.getSizeBytes() > quota.getStorageTotalBytes()) {
            throw new BizException(ErrorCodes.QUOTA_EXCEEDED, "存储配额不足");
        }

        MediaAssetEntity asset = new MediaAssetEntity();
        asset.setWorkspaceId(workspaceId());
        asset.setFolderId(request.getFolderId());
        asset.setName(request.getName().trim());
        asset.setMediaType(request.getMediaType());
        asset.setPayload(request.getPayload());
        asset.setSizeBytes(request.getSizeBytes());
        asset.setStorageKey(request.getStorageKey());
        asset.setSource(StringUtils.hasText(request.getSource()) ? request.getSource() : "upload");
        asset.setMissionExecutionId(request.getMissionExecutionId());
        asset.setRecycleStatus("normal");
        mediaAssetMapper.insert(asset);

        if (request.getTags() != null) {
            for (String tag : request.getTags()) {
                if (StringUtils.hasText(tag)) {
                    MediaTagEntity tagEntity = new MediaTagEntity();
                    tagEntity.setMediaId(asset.getId());
                    tagEntity.setTag(tag.trim());
                    mediaTagMapper.insert(tagEntity);
                }
            }
        }

        quota.setStorageUsedBytes(quota.getStorageUsedBytes() + request.getSizeBytes());
        workspaceQuotaMapper.updateById(quota);
        return toMediaVO(asset);
    }

    /**
     * 媒体库列表：固定走司空 FlightHub 原生 SDK，查询 uav.fh2（如 192.168.41.185）上的媒体。
     * 不再回退本地 t_media_asset，避免 SDK 不可用时静默返回空库。
     * {@code folderId} 保留兼容前端参数，司空媒体列表暂不按本地文件夹过滤。
     */
    public PageResult<MediaAssetVO> pageMedia(Long folderId, String mediaType, String keyword,
                                              long page, long pageSize) {
        PermissionChecker.require("library:read");
        requireFh2Sdk();
        try {
            return pageMediaFromFh2(mediaType, keyword, page, pageSize);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[media] FH2 SDK list failed: {}", e.getMessage(), e);
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                    "查询司空媒体库失败：" + e.getMessage());
        }
    }

    private PageResult<MediaAssetVO> pageMediaFromFh2(String mediaType, String keyword,
                                                      long page, long pageSize) {
        List<String> fileTypes = mapMediaTypeToFh2(mediaType);
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        if (fileTypes.isEmpty()) {
            return PageResult.of(List.of(), 0, p, size);
        }
        // 司空 SDK 按创建时间正序分页；媒体库需要倒序（最新在前），从末尾取窗口再反转。
        if (StringUtils.hasText(keyword)) {
            Fh2MediaPageVO remote = fh2SdkClient.listMediaFiles(p, size, fileTypes, keyword);
            List<MediaAssetVO> list = sortMediaDesc(remote.getList().stream()
                    .map(this::toMediaVOFromFh2)
                    .collect(Collectors.toList()));
            return PageResult.of(list, remote.getTotal(), p, size);
        }
        Fh2MediaPageVO probe = fh2SdkClient.listMediaFilesFromIndex(0, 1, fileTypes, null);
        long total = probe.getTotal();
        if (total <= 0) {
            return PageResult.of(List.of(), 0, p, size);
        }
        long endExclusive = total - (p - 1) * size;
        if (endExclusive <= 0) {
            return PageResult.of(List.of(), total, p, size);
        }
        long startIndex = Math.max(0, endExclusive - size);
        long fetchSize = endExclusive - startIndex;
        Fh2MediaPageVO remote = fh2SdkClient.listMediaFilesFromIndex(startIndex, fetchSize, fileTypes, null);
        List<MediaAssetVO> list = sortMediaDesc(remote.getList().stream()
                .map(this::toMediaVOFromFh2)
                .collect(Collectors.toList()));
        return PageResult.of(list, total, p, size);
    }

    /** 创建时间倒序；时间相同则 fileId 倒序 */
    private static List<MediaAssetVO> sortMediaDesc(List<MediaAssetVO> list) {
        if (list == null || list.size() <= 1) {
            return list == null ? List.of() : list;
        }
        list.sort((a, b) -> {
            String ca = a.getCreatedAt() == null ? "" : a.getCreatedAt();
            String cb = b.getCreatedAt() == null ? "" : b.getCreatedAt();
            int cmp = cb.compareTo(ca);
            if (cmp != 0) {
                return cmp;
            }
            long ida = a.getId() == null ? 0L : a.getId();
            long idb = b.getId() == null ? 0L : b.getId();
            return Long.compare(idb, ida);
        });
        return list;
    }

    public DownloadUrlVO downloadUrl(Long id) {
        PermissionChecker.require("library:read");
        requireFh2Sdk();
        try {
            String url = fh2SdkClient.getMediaDownloadUrl(id);
            LocalDateTime expire = LocalDateTime.now().plusHours(1);
            return DownloadUrlVO.builder()
                    .downloadUrl(url)
                    .expireAt(expire.format(FMT))
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[media] FH2 download url id={} failed: {}", id, e.getMessage(), e);
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                    "获取司空下载地址失败：" + e.getMessage());
        }
    }

    public List<MediaFolderVO> listFolders(String category) {
        PermissionChecker.require("library:read");
        LambdaQueryWrapper<MediaFolderEntity> qw = new LambdaQueryWrapper<MediaFolderEntity>()
                .eq(MediaFolderEntity::getWorkspaceId, workspaceId());
        if (StringUtils.hasText(category)) {
            qw.eq(MediaFolderEntity::getCategory, category);
        }
        qw.orderByAsc(MediaFolderEntity::getName);
        return mediaFolderMapper.selectList(qw).stream().map(this::toFolderVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public IdVO createFolder(MediaFolderCreateRequest request) {
        PermissionChecker.require("library:write");
        MediaFolderEntity folder = new MediaFolderEntity();
        folder.setWorkspaceId(workspaceId());
        folder.setName(request.getName().trim());
        folder.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory() : "media");
        folder.setParentId(request.getParentId());
        mediaFolderMapper.insert(folder);
        return new IdVO(folder.getId());
    }

    public QuotaVO quota() {
        PermissionChecker.require("library:read");
        WorkspaceQuotaEntity q = requireQuota();
        return QuotaVO.builder()
                .storageTotalBytes(q.getStorageTotalBytes())
                .storageUsedBytes(q.getStorageUsedBytes())
                .mappingQuota(q.getMappingQuota())
                .mappingUsed(q.getMappingUsed())
                .livestreamMinutesTotal(q.getLivestreamMinutesTotal())
                .livestreamMinutesUsed(q.getLivestreamMinutesUsed())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void trashMedia(Long id) {
        PermissionChecker.require("library:write");
        requireFh2Sdk();
        try {
            fh2SdkClient.deleteMediaFiles(List.of(id));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[media] FH2 delete id={} failed: {}", id, e.getMessage(), e);
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                    "删除司空媒体失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreMedia(Long id) {
        PermissionChecker.require("library:write");
        MediaAssetEntity asset = mediaAssetMapper.selectById(id);
        if (asset == null || !workspaceId().equals(asset.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MEDIA_NOT_FOUND, "媒体不存在");
        }
        asset.setRecycleStatus("normal");
        mediaAssetMapper.updateById(asset);
    }

    @Transactional(rollbackFor = Exception.class)
    public void purgeMedia(Long id) {
        PermissionChecker.require("library:write");
        MediaAssetEntity asset = mediaAssetMapper.selectById(id);
        if (asset == null || !workspaceId().equals(asset.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MEDIA_NOT_FOUND, "媒体不存在");
        }
        WorkspaceQuotaEntity quota = requireQuota();
        quota.setStorageUsedBytes(Math.max(0, quota.getStorageUsedBytes() - asset.getSizeBytes()));
        workspaceQuotaMapper.updateById(quota);
        mediaTagMapper.delete(new LambdaQueryWrapper<MediaTagEntity>().eq(MediaTagEntity::getMediaId, id));
        mediaAssetMapper.deleteById(id);
    }

    public List<TrashItemVO> trashList() {
        PermissionChecker.require("library:read");
        List<MediaAssetEntity> media = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
                .eq(MediaAssetEntity::getWorkspaceId, workspaceId())
                .eq(MediaAssetEntity::getRecycleStatus, "in_trash"));
        List<ModelAssetEntity> models = modelAssetMapper.selectList(new LambdaQueryWrapper<ModelAssetEntity>()
                .eq(ModelAssetEntity::getWorkspaceId, workspaceId())
                .eq(ModelAssetEntity::getRecycleStatus, "in_trash"));
        List<TrashItemVO> items = media.stream()
                .map(m -> TrashItemVO.media(m.getId(), m.getName(),
                        m.getCreatedAt() == null ? null : m.getCreatedAt().format(FMT)))
                .collect(Collectors.toList());
        items.addAll(models.stream()
                .map(m -> TrashItemVO.model(m.getId(), m.getName(),
                        m.getCreatedAt() == null ? null : m.getCreatedAt().format(FMT)))
                .collect(Collectors.toList()));
        return items;
    }

    public PageResult<ModelAssetVO> pageModels(long page, long pageSize) {
        PermissionChecker.require("library:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        Page<ModelAssetEntity> result = modelAssetMapper.selectPage(new Page<>(p, size),
                new LambdaQueryWrapper<ModelAssetEntity>()
                        .eq(ModelAssetEntity::getWorkspaceId, workspaceId())
                        .eq(ModelAssetEntity::getRecycleStatus, "normal")
                        .orderByDesc(ModelAssetEntity::getCreatedAt));
        List<ModelAssetVO> list = result.getRecords().stream().map(this::toModelVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReconstructJobVO createReconstructJob(ReconstructJobCreateRequest request) {
        PermissionChecker.require("library:write");
        for (Long mediaId : request.getMediaIds()) {
            requireMedia(mediaId);
        }
        ReconstructJobEntity job = new ReconstructJobEntity();
        job.setWorkspaceId(workspaceId());
        job.setStatus("pending");
        job.setCreatedBy(currentUserId());
        reconstructJobMapper.insert(job);

        ModelAssetEntity model = new ModelAssetEntity();
        model.setWorkspaceId(workspaceId());
        model.setName("reconstruct-" + job.getId());
        model.setModelType("mesh_2d");
        model.setStorageKey("models/reconstruct/" + job.getId() + ".zip");
        model.setSizeBytes(0L);
        model.setRecycleStatus("normal");
        modelAssetMapper.insert(model);

        job.setStatus("success");
        job.setModelId(model.getId());
        job.setFinishedAt(LocalDateTime.now());
        reconstructJobMapper.updateById(job);

        WorkspaceQuotaEntity quota = requireQuota();
        quota.setMappingUsed(quota.getMappingUsed() + 1);
        workspaceQuotaMapper.updateById(quota);

        return toJobVO(job);
    }

    public PageResult<ReconstructJobVO> pageReconstructJobs(long page, long pageSize) {
        PermissionChecker.require("library:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        Page<ReconstructJobEntity> result = reconstructJobMapper.selectPage(new Page<>(p, size),
                new LambdaQueryWrapper<ReconstructJobEntity>()
                        .eq(ReconstructJobEntity::getWorkspaceId, workspaceId())
                        .orderByDesc(ReconstructJobEntity::getCreatedAt));
        List<ReconstructJobVO> list = result.getRecords().stream().map(this::toJobVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    public ReconstructJobVO reconstructJobDetail(Long id) {
        PermissionChecker.require("library:read");
        ReconstructJobEntity job = reconstructJobMapper.selectById(id);
        if (job == null || !workspaceId().equals(job.getWorkspaceId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "重建任务不存在");
        }
        return toJobVO(job);
    }

    public LibraryFeaturesVO features() {
        PermissionChecker.require("library:read");
        return LibraryFeaturesVO.builder()
                .fh2SdkAvailable(fh2SdkClient.available())
                .fh2SdkError(fh2SdkClient.initError().orElse(null))
                .smartSearch(false)
                .changeDetectionPro(false)
                .ultraPanorama(false)
                .build();
    }

    private void requireFh2Sdk() {
        if (!fh2SdkClient.available()) {
            String detail = fh2SdkClient.initError().orElse("FH2 SDK 未就绪（需在 Linux/WSL 加载 native JNI）");
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "无法连接司空媒体库：" + detail);
        }
    }

    private MediaAssetEntity requireMedia(Long id) {
        MediaAssetEntity asset = mediaAssetMapper.selectById(id);
        if (asset == null || !workspaceId().equals(asset.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MEDIA_NOT_FOUND, "媒体不存在");
        }
        return asset;
    }

    private WorkspaceQuotaEntity requireQuota() {
        WorkspaceQuotaEntity quota = workspaceQuotaMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceQuotaEntity>()
                        .eq(WorkspaceQuotaEntity::getWorkspaceId, workspaceId()));
        if (quota == null) {
            throw new BizException(ErrorCodes.QUOTA_EXCEEDED, "存储配额不足");
        }
        return quota;
    }

    private MediaAssetVO toMediaVOFromFh2(Fh2MediaFileVO file) {
        return MediaAssetVO.builder()
                .id(file.getFileId())
                .name(file.getFileName())
                .mediaType(file.getMediaType())
                .payload(file.getPayloadType())
                .sizeBytes(file.getSizeBytes() == null ? 0L : file.getSizeBytes())
                .storageKey(file.getObjectKey())
                .source("fh2")
                .folderId(null)
                .recycleStatus("normal")
                .tags(Collections.emptyList())
                .createdAt(file.getCreatedAt())
                .build();
    }

    /** 司空 fileTypes：1=文件夹 2=图片 3=视频 */
    private static List<String> mapMediaTypeToFh2(String mediaType) {
        if (!StringUtils.hasText(mediaType)) {
            return List.of("2", "3");
        }
        return switch (mediaType.trim().toLowerCase()) {
            case "image" -> List.of("2");
            case "video" -> List.of("3");
            case "pointcloud" -> List.of(); // 司空媒体列表无点云类型，返回空过滤
            default -> List.of("2", "3");
        };
    }

    private MediaAssetVO toMediaVO(MediaAssetEntity asset) {
        List<MediaTagEntity> tags = mediaTagMapper.selectList(
                new LambdaQueryWrapper<MediaTagEntity>().eq(MediaTagEntity::getMediaId, asset.getId()));
        return MediaAssetVO.builder()
                .id(asset.getId())
                .name(asset.getName())
                .mediaType(asset.getMediaType())
                .payload(asset.getPayload())
                .sizeBytes(asset.getSizeBytes())
                .storageKey(asset.getStorageKey())
                .source(asset.getSource())
                .folderId(asset.getFolderId())
                .recycleStatus(asset.getRecycleStatus())
                .tags(tags.isEmpty() ? Collections.emptyList()
                        : tags.stream().map(MediaTagEntity::getTag).collect(Collectors.toList()))
                .createdAt(asset.getCreatedAt() == null ? null : asset.getCreatedAt().format(FMT))
                .build();
    }

    private MediaFolderVO toFolderVO(MediaFolderEntity f) {
        return MediaFolderVO.builder()
                .id(f.getId())
                .name(f.getName())
                .category(f.getCategory())
                .parentId(f.getParentId())
                .createdAt(f.getCreatedAt() == null ? null : f.getCreatedAt().format(FMT))
                .build();
    }

    private ModelAssetVO toModelVO(ModelAssetEntity m) {
        return ModelAssetVO.builder()
                .id(m.getId())
                .name(m.getName())
                .modelType(m.getModelType())
                .sizeBytes(m.getSizeBytes())
                .recycleStatus(m.getRecycleStatus())
                .createdAt(m.getCreatedAt() == null ? null : m.getCreatedAt().format(FMT))
                .build();
    }

    private ReconstructJobVO toJobVO(ReconstructJobEntity job) {
        return ReconstructJobVO.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .modelId(job.getModelId())
                .fh2ModelId(job.getFh2ModelId())
                .missionExecutionId(job.getMissionExecutionId())
                .previewUrl(job.getPreviewUrl())
                .modelUrl(job.getModelUrl())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt() == null ? null : job.getCreatedAt().format(FMT))
                .finishedAt(job.getFinishedAt() == null ? null : job.getFinishedAt().format(FMT))
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
