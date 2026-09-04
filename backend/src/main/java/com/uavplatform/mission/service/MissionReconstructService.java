package com.uavplatform.mission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.analytics.domain.WorkspaceQuotaEntity;
import com.uavplatform.analytics.mapper.WorkspaceQuotaMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.config.Fh2Properties;
import com.uavplatform.fh2.dto.Fh2TerraModelVO;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;
import com.uavplatform.fh2.sdk.Fh2RuntimeConfigHolder;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.media.domain.ModelAssetEntity;
import com.uavplatform.media.domain.ReconstructJobEntity;
import com.uavplatform.media.dto.DownloadUrlVO;
import com.uavplatform.media.dto.ReconstructJobVO;
import com.uavplatform.media.mapper.ModelAssetMapper;
import com.uavplatform.media.mapper.ReconstructJobMapper;
import com.uavplatform.mission.domain.MissionExecutionEntity;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.mission.dto.ExecutionReconstructVO;
import com.uavplatform.mission.dto.MissionExecutionVO;
import com.uavplatform.mission.mapper.MissionExecutionMapper;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.mission.remote.Dji2TaskClient;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.mapper.WaylineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 飞行任务三维重建：取 folder / 发起重建 / 状态同步走 FlightHub SDK；私有化无 OpenAPI，dji2 仅作兜底。
 */
@Slf4j(topic = "aiflow.reconstruct")
@Service
@RequiredArgsConstructor
public class MissionReconstructService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ACTIVE = Set.of("pending", "running");
    /** 司空 WaylineModelType：1 = 面状航线（Area） */
    private static final int FH2_WAYLINE_TYPE_AREA = 1;
    /** 本地面状航线类型（t_wayline.wayline_type，与 WaylineService 允许值对齐） */
    private static final String LOCAL_WAYLINE_TYPE_AREA = "grid_2d";
    /** 详情页约 5s 轮询，航线类型进程内缓存 5 分钟，避免反复拉司空列表 */
    private static final long WAYLINE_TYPES_CACHE_MS = 5 * 60 * 1000L;
    /** 拉取失败短冷却，防止司空不可用时被轮询打爆 */
    private static final long WAYLINE_TYPES_FAIL_COOL_DOWN_MS = 60 * 1000L;

    private final MissionPlanMapper missionPlanMapper;
    private final MissionExecutionMapper missionExecutionMapper;
    private final ReconstructJobMapper reconstructJobMapper;
    private final ModelAssetMapper modelAssetMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final WaylineMapper waylineMapper;
    private final Fh2SdkClient fh2SdkClient;
    private final Dji2TaskClient dji2TaskClient;
    private final Fh2RuntimeConfigHolder fh2ConfigHolder;

    private final ConcurrentMap<String, CachedWaylineTypes> waylineTypesCache = new ConcurrentHashMap<>();

    private record CachedWaylineTypes(List<Integer> types, boolean fetchFailed, long cachedAt) {
    }

    public void attachReconstruct(MissionPlanEntity plan, List<MissionExecutionVO> executions) {
        if (CollectionUtils.isEmpty(executions)) {
            return;
        }
        // 计划维度一次判定：面状航线才允许发起三维重建；详情页 5s 轮询，内部带缓存避免反复打司空
        boolean areaWayline = isAreaWayline(plan);
        List<Long> execIds = executions.stream()
                .map(MissionExecutionVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, ReconstructJobEntity> latestByExec = loadLatestJobs(execIds);
        for (MissionExecutionVO item : executions) {
            ReconstructJobEntity job = item.getId() == null ? null : latestByExec.get(item.getId());
            // 详情打开时纠正：司空已失败 / 无产物却被标成 success 的历史数据
            if (job != null && needsTerminalReconcile(job) && fh2SdkClient.available()) {
                try {
                    Long jobId = job.getId();
                    syncOne(job);
                    ReconstructJobEntity refreshed = reconstructJobMapper.selectById(jobId);
                    if (refreshed != null) {
                        job = refreshed;
                        latestByExec.put(item.getId(), refreshed);
                    }
                } catch (Exception e) {
                    log.debug("[mission-reconstruct] reconcile on attach skip: {}", e.getMessage());
                }
            }
            String unavailableReason = unavailableReason(item, job, areaWayline);
            boolean available = unavailableReason == null;
            if (job == null) {
                item.setReconstruct(ExecutionReconstructVO.builder()
                        .available(available)
                        .unavailableReason(unavailableReason)
                        .build());
                continue;
            }
            item.setReconstruct(ExecutionReconstructVO.builder()
                    .available(available)
                    .unavailableReason(unavailableReason)
                    .jobId(job.getId())
                    .status(job.getStatus())
                    .fh2ModelId(job.getFh2ModelId())
                    .previewUrl(job.getPreviewUrl())
                    .modelUrl(job.getModelUrl())
                    .errorMessage(job.getErrorMessage())
                    .build());
        }
    }

    /** 失败时仍落库 job，便于排查 folderIds；BizException 不触发整单回滚。 */
    @Transactional(noRollbackFor = BizException.class)
    public ReconstructJobVO start(Long planId, Long executionId) {
        PermissionChecker.require("mission:write");
        if (planId == null || executionId == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "计划或执行 ID 无效");
        }
        if (!fh2SdkClient.available()) {
            String detail = fh2SdkClient.initError().orElse("FlightHub SDK 未就绪");
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "无法发起重建：" + detail);
        }

        MissionPlanEntity plan = missionPlanMapper.selectById(planId);
        if (plan == null || !workspaceId().equals(plan.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MISSION_NOT_FOUND, "飞行计划不存在");
        }
        MissionExecutionEntity exec = missionExecutionMapper.selectById(executionId);
        if (exec == null || !planId.equals(exec.getPlanId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "执行记录不存在");
        }
        if (!StringUtils.hasText(exec.getFh2TaskUuid())) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "该任务未关联司空飞行任务，无法重建");
        }
        // 与列表按钮同一口径：仅面状航线支持三维重建
        if (!isAreaWayline(plan)) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "仅面状航线支持三维重建，该计划关联的航线不是面状航线");
        }

        ReconstructJobEntity active = findActiveJob(executionId);
        if (active != null) {
            throw new BizException(ErrorCodes.RECONSTRUCT_STATUS_INVALID, "该任务已有进行中的重建");
        }

        String taskUuid = exec.getFh2TaskUuid().trim();
        FolderCandidates folders = resolveFolderCandidates(taskUuid);
        if (folders == null || (folders.folderIds().isEmpty() && folders.folderUuids().isEmpty())) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                    "未获取到司空任务资源文件夹 ID，请确认媒体已上传到司空");
        }
        int imageCount = countFlightImages(taskUuid);
        int visibleCount = countVisibleLightImages(taskUuid);
        // 自动清除红外热成像照片（文件名含 _T），避免混入三维重建导致建图失败
        int purged = purgeThermalImages(taskUuid);
        if (purged > 0) {
            log.info("[mission-reconstruct] purged {} thermal images, recounting visible, flightId={}",
                    purged, taskUuid);
            imageCount = countFlightImages(taskUuid);
            visibleCount = countVisibleLightImages(taskUuid);
        }
        if (imageCount <= 0 && visibleCount <= 0) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                    "该架次暂无司空采集照片，无法发起三维重建");
        }
        if (visibleCount > 0 && visibleCount < 3) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                    "可用于三维重建的可见光照片不足（当前 " + visibleCount
                            + " 张，红外不计入；至少需要 3 张）。folderIds=" + folders.folderIds());
        }
        if (imageCount > 0 && imageCount < 3 && visibleCount == 0) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                    "可用于三维重建的照片不足（当前 " + imageCount + " 张，至少需要 3 张）");
        }

        ReconstructJobEntity job = new ReconstructJobEntity();
        job.setWorkspaceId(workspaceId());
        job.setMissionPlanId(planId);
        job.setMissionExecutionId(executionId);
        job.setFh2TaskUuid(taskUuid);
        job.setFh2TaskFolderId(folders.primaryId());
        job.setStatus("pending");
        job.setCreatedBy(currentUserId());
        reconstructJobMapper.insert(job);

        try {
            String modelName = "plan-" + planId + "-exec-" + executionId;
            log.info(
                    "[mission-reconstruct] call method=Fh2SdkClient.createTerraReconstruction"
                            + " params={{planId={}, executionId={}, name={}, folderIds={}, folderUuids={},"
                            + " primaryFolderId={}, fh2TaskUuid={}, images~{}, visible~{}}}",
                    planId, executionId, modelName, folders.folderIds(), folders.folderUuids(),
                    folders.primaryId(), taskUuid, imageCount, visibleCount);
            long fh2ModelId = fh2SdkClient.createTerraReconstruction(
                    modelName, folders.folderIds(), folders.folderUuids());
            job.setFh2ModelId(fh2ModelId);
            job.setStatus("running");
            reconstructJobMapper.updateById(job);
            log.info("[mission-reconstruct] started job={} exec={} modelId={} folderIds={}",
                    job.getId(), executionId, fh2ModelId, folders.folderIds());
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("222001") || msg.contains("输入图像太少")) {
                int ir = Math.max(0, imageCount - visibleCount);
                msg = msg + "；本架次统计：可见光约 " + visibleCount + " 张、红外约 " + ir
                        + " 张（红外一般不能单独建三维）。请到司空控制台对同一媒体文件夹手动点一次「三维重建」："
                        + "若控制台也失败，需补拍更多可见光/提高重叠；若控制台成功，把结果告知以便继续对参数。";
            }
            job.setStatus("failed");
            job.setErrorMessage(truncate(msg, 480));
            job.setFinishedAt(LocalDateTime.now());
            reconstructJobMapper.updateById(job);
            log.error("[mission-reconstruct] start failed job={} exec={} folderIds={}: {}",
                    job.getId(), executionId, folders.folderIds(), msg);
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "发起三维重建失败：" + msg);
        }
        return toVO(job);
    }

    /** 轮询进行中的重建任务，并纠正「假成功」（无 ZIP 的 success） */
    public void syncOpenJobs() {
        if (!fh2SdkClient.available()) {
            return;
        }
        List<ReconstructJobEntity> jobs = reconstructJobMapper.selectList(new LambdaQueryWrapper<ReconstructJobEntity>()
                .isNotNull(ReconstructJobEntity::getFh2ModelId)
                .and(w -> w.in(ReconstructJobEntity::getStatus, ACTIVE)
                        .or(w2 -> w2.eq(ReconstructJobEntity::getStatus, "success")
                                .and(w3 -> w3.isNull(ReconstructJobEntity::getModelUrl)
                                        .or()
                                        .eq(ReconstructJobEntity::getModelUrl, ""))))
                .orderByDesc(ReconstructJobEntity::getId)
                .last("LIMIT 50"));
        if (jobs.isEmpty()) {
            return;
        }
        for (ReconstructJobEntity job : jobs) {
            try {
                syncOne(job);
            } catch (Exception e) {
                log.warn("[mission-reconstruct] sync job {} failed: {}", job.getId(), e.getMessage());
            }
        }
    }

    /**
     * 解析三维模型 ZIP 下载地址（司空签名链可能过期，优先实时向司空取）。
     */
    public DownloadUrlVO resolveModelDownloadUrl(Long planId, Long executionId) {
        try {
            return doResolveModelDownloadUrl(planId, executionId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[mission-reconstruct] resolveModelDownloadUrl plan={} exec={} failed",
                    planId, executionId, e);
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                    "获取模型下载地址失败：" + truncate(rootMessage(e), 200));
        }
    }

    private DownloadUrlVO doResolveModelDownloadUrl(Long planId, Long executionId) {
        PermissionChecker.require("mission:read");
        if (planId == null || executionId == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "计划或执行 ID 无效");
        }
        MissionPlanEntity plan = missionPlanMapper.selectById(planId);
        if (plan == null || !workspaceId().equals(plan.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MISSION_NOT_FOUND, "飞行计划不存在");
        }
        MissionExecutionEntity exec = missionExecutionMapper.selectById(executionId);
        if (exec == null || !planId.equals(exec.getPlanId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "执行记录不存在");
        }
        ReconstructJobEntity job = loadLatestJobs(List.of(executionId)).get(executionId);
        if (job == null) {
            throw new BizException(ErrorCodes.RECONSTRUCT_STATUS_INVALID, "未找到重建任务");
        }
        String st = normalize(job.getStatus());
        if (!"success".equals(st) && !"running".equals(st)) {
            throw new BizException(ErrorCodes.RECONSTRUCT_STATUS_INVALID, "重建尚未完成，暂无模型下载地址");
        }
        if (job.getFh2ModelId() == null) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "重建任务缺少司空模型 ID");
        }
        if (!fh2SdkClient.available()) {
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                    "无法获取模型地址：" + fh2SdkClient.initError().orElse("FlightHub SDK 未就绪"));
        }

        int modelId = job.getFh2ModelId().intValue();
        ZipResolveResult resolved = resolveZipUrlFromFh2(modelId);
        String url = resolved.url();
        if (!StringUtils.hasText(url)) {
            url = job.getModelUrl();
        }
        if (!StringUtils.hasText(url)) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, formatZipMissingTip(modelId, resolved.error()));
        }
        // 签名 URL 可能很长，落库失败不影响本次下载
        persistModelUrlBestEffort(job, url);
        if (StringUtils.hasText(resolved.thumbnailUrl())) {
            try {
                if (!resolved.thumbnailUrl().equals(job.getPreviewUrl())) {
                    job.setPreviewUrl(resolved.thumbnailUrl());
                    reconstructJobMapper.updateById(job);
                }
            } catch (Exception e) {
                log.debug("[mission-reconstruct] persist previewUrl skip: {}", e.getMessage());
            }
        }
        return DownloadUrlVO.builder()
                .downloadUrl(url)
                .expireAt(LocalDateTime.now().plusHours(1).format(FMT))
                .build();
    }

    /**
     * 优先用 getModelFileList 里的 zipUrl；没有再试 getModelFileUrl 多种 type。
     * type 文档仅写「类型」，私有化常见 1=原始 zip，这里对 1/0/2 做兜底。
     */
    private ZipResolveResult resolveZipUrlFromFh2(int modelId) {
        String thumbnail = null;
        String zipFromList = null;
        Fh2TerraModelVO found = findTerraModelById(modelId);
        if (found != null) {
            thumbnail = found.getThumbnailUrl();
            zipFromList = found.getZipUrl();
            if (StringUtils.hasText(zipFromList)) {
                return new ZipResolveResult(zipFromList.trim(), thumbnail, null);
            }
        }
        String lastError = null;
        for (int type : new int[]{1, 0, 2}) {
            try {
                String url = fh2SdkClient.getTerraModelFileUrl(modelId, type);
                if (StringUtils.hasText(url)) {
                    return new ZipResolveResult(url.trim(), thumbnail, null);
                }
                lastError = "getModelFileUrl(type=" + type + ") 返回空（模型可能仍在压缩打包）";
            } catch (Exception e) {
                lastError = rootMessage(e);
                log.warn("[mission-reconstruct] getTerraModelFileUrl modelId={} type={} failed: {}",
                        modelId, type, lastError);
            }
        }
        return new ZipResolveResult(null, thumbnail, lastError);
    }

    private Fh2TerraModelVO findTerraModelById(int modelId) {
        try {
            for (int page = 1; page <= 5; page++) {
                List<Fh2TerraModelVO> list = fh2SdkClient.listTerraModels(page, 50);
                if (CollectionUtils.isEmpty(list)) {
                    break;
                }
                for (Fh2TerraModelVO item : list) {
                    if (item != null && item.getId() != null && item.getId() == modelId) {
                        return item;
                    }
                }
                if (list.size() < 50) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("[mission-reconstruct] listTerraModels for modelId={} failed: {}", modelId, e.getMessage());
        }
        return null;
    }

    private static String formatZipMissingTip(int modelId, String sdkError) {
        String tip = "暂未获取到模型 ZIP 地址（司空模型 ID=" + modelId + "）。"
                + "重建完成后 ZIP 往往还需打包一段时间；请稍后重试，或到司空 Terra 模型库下载。";
        if (!StringUtils.hasText(sdkError)) {
            return tip;
        }
        if (sdkError.contains("222101")) {
            return tip + " 司空返回 222101（重建内部错误），常见于 ZIP 未打好或产物异常。";
        }
        return tip + "（" + truncate(sdkError, 160) + "）";
    }

    private record ZipResolveResult(String url, String thumbnailUrl, String error) {
    }

    private void persistModelUrlBestEffort(ReconstructJobEntity job, String url) {
        if (job == null || !StringUtils.hasText(url) || url.equals(job.getModelUrl())) {
            return;
        }
        try {
            // 兼容未执行 TEXT 升级的库：超长则跳过落库，只返回给前端
            if (url.length() > 1000) {
                log.debug("[mission-reconstruct] skip persist long modelUrl len={}", url.length());
                return;
            }
            job.setModelUrl(url);
            reconstructJobMapper.updateById(job);
        } catch (Exception e) {
            log.warn("[mission-reconstruct] persist modelUrl job={} failed: {}", job.getId(), e.getMessage());
        }
    }

    private void syncOne(ReconstructJobEntity job) {
        if (job.getFh2ModelId() == null) {
            return;
        }
        int modelId = job.getFh2ModelId().intValue();
        Fh2TerraModelVO found;
        try {
            found = findTerraModelById(modelId);
        } catch (Exception e) {
            log.debug("[mission-reconstruct] listTerraModels failed: {}", e.getMessage());
            if (job.getCreatedAt() != null && job.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                markFailed(job, "重建超时（超过 24 小时未完成）");
            }
            return;
        }
        if (found == null) {
            return;
        }
        Integer state = found.getState();
        if (isFh2FailedState(state)
                || (state != null && (state == 2 || state == 15 || state == 6))) {
            log.info("[mission-reconstruct] sync job={} modelId={} status={} fh2State={} name={}",
                    job.getId(), modelId, job.getStatus(), state, found.getName());
        } else {
            log.debug("[mission-reconstruct] sync job={} modelId={} status={} fh2State={}",
                    job.getId(), modelId, job.getStatus(), state);
        }
        if (StringUtils.hasText(found.getThumbnailUrl())) {
            job.setPreviewUrl(found.getThumbnailUrl());
        }

        // 司空终态失败（含 state=8 建图失败）
        if (isFh2FailedState(state)) {
            int errorCode = 0;
            try {
                errorCode = fh2SdkClient.getReconstructionErrorCode(modelId);
            } catch (Exception ignored) { }
            String msg = fh2StateMessage(state);
            if (errorCode != 0) {
                msg = msg + "（error_code=" + errorCode + "）";
            }
            // 诊断：查询重建引擎实际收到的照片数
            if (StringUtils.hasText(found.getUuid())) {
                try {
                    int imgCount = fh2SdkClient.getReconstructionImageCount(found.getUuid());
                    if (imgCount >= 0) {
                        log.warn("[mission-reconstruct] job={} modelId={} failed diag: imageCount={} (errorCode={})",
                                job.getId(), modelId, imgCount, errorCode);
                    }
                } catch (Exception e) {
                    log.debug("[mission-reconstruct] getReconstructionImageCount failed: {}", e.getMessage());
                }
            }
            markFailed(job, msg);
            return;
        }
        // 仍在重建 / 压缩 / 等待资源
        if (isFh2InProgressState(state) || state == null || state == 0) {
            if (ACTIVE.contains(normalize(job.getStatus()))) {
                reconstructJobMapper.updateById(job);
            }
            return;
        }

        // 重建成功或 ZIP 已就绪：必须拿到 zip 才标 success
        ZipResolveResult zip = resolveZipUrlFromFh2(modelId);
        if (StringUtils.hasText(zip.url())) {
            if (zip.url().length() <= 1000) {
                job.setModelUrl(zip.url());
            }
            markSuccess(job, found);
            return;
        }
        // ZIP 压缩失败
        if (state != null && state == 3) {
            markFailed(job, "模型 ZIP 压缩失败");
            return;
        }
        // 重建成功但 ZIP 尚未打好：继续 running；超时则按失败（避免假成功）
        if (ACTIVE.contains(normalize(job.getStatus())) || "success".equalsIgnoreCase(normalize(job.getStatus()))) {
            if (!"running".equalsIgnoreCase(normalize(job.getStatus()))) {
                job.setStatus("running");
            }
            reconstructJobMapper.updateById(job);
        }
        boolean waitTooLong = job.getCreatedAt() != null
                && job.getCreatedAt().isBefore(LocalDateTime.now().minusHours(2));
        if (waitTooLong) {
            String detail = StringUtils.hasText(zip.error()) ? zip.error() : "超时未拿到 ZIP";
            markFailed(job, "重建产物不可下载：" + truncate(detail, 200));
            return;
        }
        log.info("[mission-reconstruct] modelId={} state={} waiting zip: {}", modelId, state, zip.error());
    }

    private static boolean needsTerminalReconcile(ReconstructJobEntity job) {
        if (job.getFh2ModelId() == null) {
            return false;
        }
        String st = normalize(job.getStatus());
        if (ACTIVE.contains(st)) {
            return true;
        }
        // 假成功：标了 success 但没有 modelUrl
        if ("success".equals(st) && !StringUtils.hasText(job.getModelUrl())) {
            return true;
        }
        // 仅纠正旧失败文案，避免详情轮询反复打司空
        String err = job.getErrorMessage();
        return "failed".equals(st) && (err == null || err.contains("701027") || err.contains("state=8"));
    }

    /** 司空失败终态：3 ZIP失败 / 7 格式转换失败 / 8 建图失败 / 11 校验失败 / 13 等资源失败 / 16 取消 / 119+ 快速重建失败等 */
    private static boolean isFh2FailedState(Integer state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case 3, 7, 8, 11, 13, 16, 119, 122, 123, 125 -> true;
            default -> false;
        };
    }

    private static boolean isFh2InProgressState(Integer state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case 1, 4, 5, 10, 12, 14, 17, 120, 121, 124 -> true;
            default -> false;
        };
    }

    private static String fh2StateMessage(Integer state) {
        if (state == null) {
            return "司空重建失败";
        }
        return switch (state) {
            case 3 -> "模型 ZIP 压缩失败，请重新重建";
            case 7 -> "模型格式转换失败，请重新重建";
            case 8 -> "建图失败：司空未产出有效三维模型（无 index/ZIP）。请检查航拍重叠与 POS 后重新重建";
            case 11 -> "模型格式校验失败，请重新重建";
            case 13 -> "等待重建资源失败，请稍后重新重建";
            case 16 -> "重建已取消，可重新重建";
            case 119, 122, 123, 125 -> "空三/快速重建失败，请重新重建";
            default -> "司空重建失败（state=" + state + "），请重新重建";
        };
    }

    private void markFailed(ReconstructJobEntity job, String message) {
        job.setStatus("failed");
        job.setErrorMessage(truncate(message, 480));
        job.setFinishedAt(LocalDateTime.now());
        reconstructJobMapper.updateById(job);
        log.warn("[mission-reconstruct] job={} modelId={} marked failed: {}",
                job.getId(), job.getFh2ModelId(), message);
    }

    private void markSuccess(ReconstructJobEntity job, Fh2TerraModelVO found) {
        job.setStatus("success");
        job.setErrorMessage(null);
        job.setFinishedAt(LocalDateTime.now());
        if (job.getModelId() == null) {
            ModelAssetEntity model = new ModelAssetEntity();
            model.setWorkspaceId(job.getWorkspaceId());
            model.setName(StringUtils.hasText(found.getName()) ? found.getName()
                    : ("fh2-model-" + job.getFh2ModelId()));
            model.setModelType("mesh_3d");
            model.setStorageKey("fh2-model:" + job.getFh2ModelId());
            model.setSizeBytes(0L);
            model.setRecycleStatus("normal");
            modelAssetMapper.insert(model);
            job.setModelId(model.getId());
            bumpMappingQuota(job.getWorkspaceId());
        }
        reconstructJobMapper.updateById(job);
        log.info("[mission-reconstruct] job={} modelId={} marked success",
                job.getId(), job.getFh2ModelId());
    }
    private FolderCandidates resolveFolderCandidates(String taskUuid) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        LinkedHashSet<String> uuids = new LinkedHashSet<>();
        Long primary = null;
        if (dji2TaskClient.configured()) {
            // 分开 try-catch：getFlightTaskDetail 可能 404，需让 getTaskSubDetail 回退正常执行
            Map<String, Object> detail = Map.of();
            try {
                detail = dji2TaskClient.getFlightTaskDetail(taskUuid);
                if (!detail.isEmpty()) {
                    log.info("[mission-reconstruct] dji2 flighttask detail ok, flightId={}", taskUuid);
                }
            } catch (Exception e) {
                log.debug("[mission-reconstruct] dji2 flighttask detail failed, fallback to task sub: {}", e.getMessage());
            }
            if (detail.isEmpty()) {
                try {
                    detail = dji2TaskClient.getTaskSubDetail(taskUuid);
                    if (!detail.isEmpty()) {
                        log.info("[mission-reconstruct] dji2 task sub detail ok, flightId={}", taskUuid);
                    }
                } catch (Exception e) {
                    log.warn("[mission-reconstruct] dji2 task detail folder failed: {}", e.getMessage());
                }
            }
            if (!detail.isEmpty()) {
                Long folderId = extractFolderId(detail);
                if (folderId != null) {
                    primary = folderId;
                    ids.add(folderId.intValue());
                    log.info("[mission-reconstruct] folderId={} from dji2 task detail, flightId={}", folderId, taskUuid);
                }
                String folderUuid = extractFolderUuid(detail);
                if (StringUtils.hasText(folderUuid)) {
                    uuids.add(folderUuid.trim());
                    log.info("[mission-reconstruct] folderUuid={} from dji2 task detail, flightId={}", folderUuid, taskUuid);
                }
            }
        }
        FolderCandidates fromMedia = resolveFolderCandidatesFromMedia(taskUuid);
        if (fromMedia != null) {
            if (primary == null) {
                primary = fromMedia.primaryId();
            }
            ids.addAll(fromMedia.folderIds());
            uuids.addAll(fromMedia.folderUuids());
        }
        if (ids.isEmpty() && uuids.isEmpty()) {
            return null;
        }
        // folderUuid 未从 task detail / 媒体获取到时，依次尝试：
        // 1. SDK getReconstructionSettings 按 folderIds 匹配反查（只返回与当前 folderIds 匹配的 UUID）
        // 2. 配置的手动值（与 demo ConfigManager.terraBuildFolderUuid 对齐）
        if (uuids.isEmpty() && !ids.isEmpty()) {
            try {
                String resolved = fh2SdkClient.resolveTerraFolderUuidByFolderIds(ids);
                if (StringUtils.hasText(resolved)) {
                    uuids.add(resolved.trim());
                    log.info("[mission-reconstruct] resolved folderUuid={} (matched folderIds={}), flightId={}",
                            resolved, ids, taskUuid);
                }
            } catch (Exception e) {
                log.debug("[mission-reconstruct] resolveTerraFolderUuidByFolderIds failed: {}", e.getMessage());
            }
        }
        if (uuids.isEmpty()) {
            try {
                Fh2Properties cfg = fh2ConfigHolder.effective();
                if (StringUtils.hasText(cfg.getTerraBuildFolderUuid())) {
                    uuids.add(cfg.getTerraBuildFolderUuid().trim());
                    log.info("[mission-reconstruct] using configured terraBuildFolderUuid={}, flightId={}",
                            cfg.getTerraBuildFolderUuid(), taskUuid);
                }
            } catch (Exception ignored) {
                // 配置读取失败不影响主流程
            }
        }
        if (primary == null && !ids.isEmpty()) {
            primary = ids.iterator().next().longValue();
        }
        log.info("[mission-reconstruct] resolved folders: folderIds={} folderUuids={} primary={}, flightId={}",
                ids, uuids, primary, taskUuid);
        return new FolderCandidates(primary, new ArrayList<>(ids), new ArrayList<>(uuids));
    }

    /** 统计架次下司空照片数（用于重建前置提示；查不到时返回 0 不拦截）。 */
    private int countFlightImages(String flightId) {
        if (!fh2SdkClient.available()) {
            return 0;
        }
        try {
            var page = fh2SdkClient.listMediaFilesByFlightId(flightId, 1, 50, List.of("2"), null);
            if (page == null) {
                return 0;
            }
            if (page.getTotal() > 0) {
                return (int) Math.min(page.getTotal(), Integer.MAX_VALUE);
            }
            return CollectionUtils.isEmpty(page.getList()) ? 0 : page.getList().size();
        } catch (Exception e) {
            log.debug("[mission-reconstruct] count images skip: {}", e.getMessage());
            return 0;
        }
    }

    /** 可见光照片数（文件名含 _V，排除红外 _T）；完整分页遍历。 */
    private int countVisibleLightImages(String flightId) {
        if (!fh2SdkClient.available()) {
            return 0;
        }
        try {
            int n = 0;
            long page = 1;
            while (true) {
                var result = fh2SdkClient.listMediaFilesByFlightId(flightId, page, 200, List.of("2"), null);
                if (result == null || CollectionUtils.isEmpty(result.getList())) {
                    break;
                }
                for (var f : result.getList()) {
                    if (f == null) {
                        continue;
                    }
                    String name = f.getFileName() == null ? "" : f.getFileName();
                    if (name.contains("_T.") || name.contains("_T_")) {
                        continue;
                    }
                    n++;
                }
                if (result.getTotal() <= 0 || page * 200 >= result.getTotal()) {
                    break;
                }
                page++;
            }
            return n;
        } catch (Exception e) {
            log.debug("[mission-reconstruct] count visible skip: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 遍历架次全部图片，删除红外热成像照片（文件名含 _T），避免混入三维重建。
     * SDK deleteMediaFiles 每批最多 100 条，分批删除。
     */
    private int purgeThermalImages(String flightId) {
        if (!fh2SdkClient.available()) {
            return 0;
        }
        List<Long> thermalIds = new ArrayList<>();
        try {
            long page = 1;
            while (true) {
                var result = fh2SdkClient.listMediaFilesByFlightId(flightId, page, 200, List.of("2"), null);
                if (result == null || CollectionUtils.isEmpty(result.getList())) {
                    break;
                }
                for (var f : result.getList()) {
                    if (f == null || f.getFileId() == null) {
                        continue;
                    }
                    String name = f.getFileName() == null ? "" : f.getFileName();
                    if (name.contains("_T.") || name.contains("_T_")) {
                        thermalIds.add(f.getFileId());
                    }
                }
                // 已经遍历完所有页
                if (result.getTotal() <= 0 || page * 200 >= result.getTotal()) {
                    break;
                }
                page++;
            }
            if (thermalIds.isEmpty()) {
                return 0;
            }
            log.info("[mission-reconstruct] found {} thermal images to delete, flightId={}", thermalIds.size(), flightId);
            int deleted = 0;
            // 分批删除（SDK 每批上限 100）
            for (int i = 0; i < thermalIds.size(); i += 100) {
                List<Long> batch = thermalIds.subList(i, Math.min(i + 100, thermalIds.size()));
                fh2SdkClient.deleteMediaFiles(batch);
                deleted += batch.size();
                log.info("[mission-reconstruct] deleted {} thermal images (batch {}), flightId={}",
                        batch.size(), i / 100 + 1, flightId);
            }
            return deleted;
        } catch (Exception e) {
            log.warn("[mission-reconstruct] purgeThermalImages failed, will continue: {}", e.getMessage());
            return 0;
        }
    }

    /** 从媒体 filePath 解析文件夹候选；优先 SDK，仅看图片。 */
    private FolderCandidates resolveFolderCandidatesFromMedia(String flightId) {
        List<Object> paths = new ArrayList<>();
        if (fh2SdkClient.available()) {
            try {
                var page = fh2SdkClient.listMediaFilesByFlightId(flightId, 1, 50, List.of("2"), null);
                if (page != null && !CollectionUtils.isEmpty(page.getList())) {
                    for (var f : page.getList()) {
                        if (f != null && StringUtils.hasText(f.getFilePath())) {
                            paths.add(f.getFilePath());
                        }
                    }
                    if (!page.getList().isEmpty()) {
                        var sample = page.getList().get(0);
                        log.info("[mission-reconstruct] SDK media sample: objectKey={} filePath={}",
                                sample.getObjectKey(), sample.getFilePath());
                    }
                }
            } catch (Exception e) {
                log.warn("[mission-reconstruct] SDK media folder failed: {}", e.getMessage());
            }
        }
        if (paths.isEmpty() && dji2TaskClient.configured()) {
            try {
                List<Map<String, Object>> media = dji2TaskClient.listMediaByFlightId(flightId, 1, 50);
                for (Map<String, Object> item : media) {
                    if (item == null) {
                        continue;
                    }
                    Object p = item.get("filePath");
                    if (p == null) {
                        p = item.get("file_path");
                    }
                    if (p != null) {
                        paths.add(p);
                    }
                }
            } catch (Exception e) {
                log.warn("[mission-reconstruct] dji2 media folder failed: {}", e.getMessage());
            }
        }
        FolderCandidates c = majorityFolderCandidates(paths);
        if (c != null) {
            log.info("[mission-reconstruct] folderIds={} primary={} from media filePath, flightId={}",
                    c.folderIds(), c.primaryId(), flightId);
        }
        return c;
    }

    /** 多条媒体路径合并文件夹候选（末段子目录优先，并带上倒数第二段任务夹）。 */
    static FolderCandidates majorityFolderCandidates(List<?> filePaths) {
        if (CollectionUtils.isEmpty(filePaths)) {
            return null;
        }
        Map<String, Integer> pathCounts = new HashMap<>();
        String bestPath = null;
        int bestCount = 0;
        for (Object path : filePaths) {
            if (path == null) {
                continue;
            }
            String p = String.valueOf(path).trim();
            if (p.isEmpty() || "null".equalsIgnoreCase(p)) {
                continue;
            }
            int c = pathCounts.merge(p, 1, Integer::sum);
            if (c > bestCount) {
                bestCount = c;
                bestPath = p;
            }
        }
        if (bestPath == null) {
            return null;
        }
        return folderCandidatesFromFilePath(bestPath);
    }

    record FolderCandidates(Long primaryId, List<Integer> folderIds, List<String> folderUuids) {
        FolderCandidates(Long primaryId, List<Integer> folderIds) {
            this(primaryId, folderIds, List.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractFolderUuid(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        String direct = textField(detail, "folder_uuid", "folderUuid", "task_folder_uuid", "media_folder_uuid");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        Object folderInfo = detail.get("folder_info");
        if (folderInfo == null) {
            folderInfo = detail.get("folderInfo");
        }
        if (folderInfo instanceof Map<?, ?> map) {
            Map<String, Object> folderMap = (Map<String, Object>) map;
            return textField(folderMap, "uuid", "folder_uuid", "folderUuid");
        }
        return null;
    }

    private static String textField(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && StringUtils.hasText(String.valueOf(v))) {
                return String.valueOf(v).trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Long extractFolderId(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        Long direct = toLong(detail.get("folder_id"));
        if (direct == null) {
            direct = toLong(detail.get("folderId"));
        }
        if (direct != null && direct > 0) {
            return direct;
        }
        Object folderInfo = detail.get("folder_info");
        if (folderInfo == null) {
            folderInfo = detail.get("folderInfo");
        }
        if (folderInfo instanceof Map<?, ?> map) {
            Long id = toLong(map.get("folder_id"));
            if (id == null) {
                id = toLong(map.get("folderId"));
            }
            if (id != null && id > 0) {
                return id;
            }
        }
        return null;
    }

    /**
     * 从媒体 filePath 解析重建文件夹候选。
     * <p>路径 {@code 83/102/103}：primary=末段 103（照片所在目录），
     * 同时传入全部路径段 [103, 102, 83]（含顶层文件夹），
     * 确保 SDK 能递归扫描到完整照片集。
     */
    static FolderCandidates folderCandidatesFromFilePath(Object filePath) {
        List<Long> segments = numericPathSegments(filePath);
        if (segments.isEmpty()) {
            return null;
        }
        Long leaf = segments.get(segments.size() - 1);
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        // 传入全部路径段：从末段（照片子目录）到首段（顶层媒体文件夹）
        for (int i = segments.size() - 1; i >= 0; i--) {
            ids.add(segments.get(i).intValue());
        }
        return new FolderCandidates(leaf, new ArrayList<>(ids));
    }

    /** 兼容旧逻辑：取照片所在目录（路径末段）。 */
    static Long folderIdFromFilePath(Object filePath) {
        FolderCandidates c = folderCandidatesFromFilePath(filePath);
        return c == null ? null : c.primaryId();
    }

    /** filePath 中的数字段，按路径顺序；非法或空返回空列表。 */
    static List<Long> numericPathSegments(Object filePath) {
        if (filePath == null) {
            return List.of();
        }
        String path = String.valueOf(filePath).trim();
        if (path.isEmpty() || "null".equalsIgnoreCase(path)) {
            return List.of();
        }
        List<Long> out = new java.util.ArrayList<>();
        for (String part : path.split("[/\\\\]")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            Long id = toLong(part.trim());
            if (id != null && id > 0) {
                out.add(id);
            }
        }
        return out;
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Long, ReconstructJobEntity> loadLatestJobs(List<Long> execIds) {
        Map<Long, ReconstructJobEntity> map = new HashMap<>();
        if (execIds.isEmpty()) {
            return map;
        }
        List<ReconstructJobEntity> jobs = reconstructJobMapper.selectList(new LambdaQueryWrapper<ReconstructJobEntity>()
                .eq(ReconstructJobEntity::getWorkspaceId, workspaceId())
                .in(ReconstructJobEntity::getMissionExecutionId, execIds)
                .orderByDesc(ReconstructJobEntity::getId));
        for (ReconstructJobEntity job : jobs) {
            if (job.getMissionExecutionId() == null) {
                continue;
            }
            map.putIfAbsent(job.getMissionExecutionId(), job);
        }
        return map;
    }

    private ReconstructJobEntity findActiveJob(Long executionId) {
        return reconstructJobMapper.selectOne(new LambdaQueryWrapper<ReconstructJobEntity>()
                .eq(ReconstructJobEntity::getMissionExecutionId, executionId)
                .in(ReconstructJobEntity::getStatus, ACTIVE)
                .orderByDesc(ReconstructJobEntity::getId)
                .last("LIMIT 1"));
    }

    /** 可发起重建的前置校验；返回 null 表示可发起，否则返回不可发起原因（前端 tooltip 用） */
    private String unavailableReason(MissionExecutionVO item, ReconstructJobEntity latest, boolean areaWayline) {
        if (item == null || item.getId() == null || !"execution".equals(item.getRecordType())) {
            return "no_task";
        }
        if (!StringUtils.hasText(item.getFh2TaskUuid())) {
            return "no_task";
        }
        if (CollectionUtils.isEmpty(item.getMedia())) {
            return "no_media";
        }
        if (!areaWayline) {
            return "not_area_wayline";
        }
        if (latest != null && ACTIVE.contains(normalize(latest.getStatus()))) {
            return "active_job";
        }
        return null;
    }

    /** 是否面状航线：本地已标记面状直接认定；否则查司空航线类型（懒校准回写本地），查询失败按非面状处理。供 detail 组装 plan VO 复用。 */
    public boolean isAreaWayline(MissionPlanEntity plan) {
        if (plan == null || plan.getWaylineId() == null) {
            return false;
        }
        WaylineEntity wayline = waylineMapper.selectById(plan.getWaylineId());
        if (wayline == null) {
            return false;
        }
        if (LOCAL_WAYLINE_TYPE_AREA.equals(wayline.getWaylineType())) {
            return true;
        }
        String fh2Uuid = wayline.getFh2WaylineUuid();
        if (!StringUtils.hasText(fh2Uuid)) {
            return false;
        }
        List<Integer> types = cachedWaylineTypes(fh2Uuid.trim());
        if (types == null || !types.contains(FH2_WAYLINE_TYPE_AREA)) {
            return false;
        }
        // 懒校准：把司空侧识别出的面状航线回写本地，后续短路且列表页可见
        try {
            wayline.setWaylineType(LOCAL_WAYLINE_TYPE_AREA);
            waylineMapper.updateById(wayline);
            log.info("[mission-reconstruct] calibrated wayline {} to {} by FH2 waylineTypes",
                    fh2Uuid, LOCAL_WAYLINE_TYPE_AREA);
        } catch (Exception e) {
            log.debug("[mission-reconstruct] persist area wayline type skip: {}", e.getMessage());
        }
        return true;
    }

    /** 优先读缓存；未命中翻司空航线列表（整页入缓存供其他计划复用），失败短冷却防轮询打爆 */
    private List<Integer> cachedWaylineTypes(String fh2Uuid) {
        CachedWaylineTypes cached = waylineTypesCache.get(fh2Uuid);
        long now = System.currentTimeMillis();
        if (cached != null) {
            long ttl = cached.fetchFailed() ? WAYLINE_TYPES_FAIL_COOL_DOWN_MS : WAYLINE_TYPES_CACHE_MS;
            if (now - cached.cachedAt() < ttl) {
                return cached.types();
            }
        }
        if (!fh2SdkClient.available()) {
            return cached != null ? cached.types() : null;
        }
        try {
            List<Integer> result = null;
            for (int page = 0; page <= 4; page++) {
                List<Fh2WaylineItemVO> items = fh2SdkClient.listWaylines(page, 200);
                if (CollectionUtils.isEmpty(items)) {
                    break;
                }
                for (Fh2WaylineItemVO w : items) {
                    if (w == null || !StringUtils.hasText(w.getWaylineUuid())) {
                        continue;
                    }
                    List<Integer> t = w.getWaylineTypes() == null ? List.of() : w.getWaylineTypes();
                    waylineTypesCache.put(w.getWaylineUuid().trim(),
                            new CachedWaylineTypes(t, false, now));
                    if (fh2Uuid.equals(w.getWaylineUuid().trim())) {
                        result = t;
                    }
                }
                if (items.size() < 200) {
                    break;
                }
            }
            if (result == null) {
                // 司空列表里已无此航线：按非面状缓存
                result = List.of();
                waylineTypesCache.put(fh2Uuid, new CachedWaylineTypes(result, false, now));
            }
            return result;
        } catch (Exception e) {
            log.warn("[mission-reconstruct] list wayline types for {} failed: {}", fh2Uuid, e.getMessage());
            waylineTypesCache.put(fh2Uuid, new CachedWaylineTypes(null, true, now));
            return null;
        }
    }

    private void bumpMappingQuota(Long workspaceId) {
        WorkspaceQuotaEntity quota = workspaceQuotaMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceQuotaEntity>()
                        .eq(WorkspaceQuotaEntity::getWorkspaceId, workspaceId));
        if (quota == null) {
            return;
        }
        Integer used = quota.getMappingUsed();
        quota.setMappingUsed((used == null ? 0 : used) + 1);
        workspaceQuotaMapper.updateById(quota);
    }

    private ReconstructJobVO toVO(ReconstructJobEntity job) {
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

    private static String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String rootMessage(Throwable e) {
        if (e == null) {
            return "unknown";
        }
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (!StringUtils.hasText(msg)) {
            msg = e.getMessage();
        }
        if (!StringUtils.hasText(msg)) {
            msg = e.getClass().getSimpleName();
        }
        return msg;
    }
}
