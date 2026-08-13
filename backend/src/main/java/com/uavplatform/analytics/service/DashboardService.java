package com.uavplatform.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.algorithm.mapper.AlgorithmAlarmMapper;
import com.uavplatform.analytics.config.QuotaProperties;
import com.uavplatform.analytics.domain.DashboardSnapshotEntity;
import com.uavplatform.analytics.domain.WorkspaceQuotaEntity;
import com.uavplatform.analytics.dto.AutomationVO;
import com.uavplatform.analytics.dto.DashboardSummaryVO;
import com.uavplatform.analytics.dto.DockStatusVO;
import com.uavplatform.analytics.dto.FlightStatPointVO;
import com.uavplatform.analytics.dto.FlightStatsVO;
import com.uavplatform.analytics.dto.MissionStatPointVO;
import com.uavplatform.analytics.dto.MissionStatsVO;
import com.uavplatform.analytics.dto.QuotaVO;
import com.uavplatform.analytics.mapper.DashboardSnapshotMapper;
import com.uavplatform.analytics.mapper.WorkspaceQuotaMapper;
import com.uavplatform.analytics.util.TimeRangeHelper;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.dto.Fh2MediaFileVO;
import com.uavplatform.fh2.dto.Fh2MediaPageVO;
import com.uavplatform.fh2.dto.Fh2TerraModelVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.mapper.UserMapper;
import com.uavplatform.media.domain.MediaAssetEntity;
import com.uavplatform.media.domain.ModelAssetEntity;
import com.uavplatform.media.mapper.MediaAssetMapper;
import com.uavplatform.media.mapper.ModelAssetMapper;
import com.uavplatform.mission.domain.MissionExecutionEntity;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.mission.mapper.MissionExecutionMapper;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.mapper.WaylineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 首页看板。媒体/存储优先读司空 FH2；任务与飞行从执行记录实时聚合（日表未落库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> FH2_MEDIA_TYPES = List.of("2", "3");
    /** 精确累加 FH2 文件体积的上限；超出则按首屏均值估算，避免首页阻塞 */
    private static final long FH2_SIZE_EXACT_CAP = 2000L;
    private static final long FH2_PAGE_SIZE = 200L;

    private final DashboardSnapshotMapper dashboardSnapshotMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final UserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final WaylineMapper waylineMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final ModelAssetMapper modelAssetMapper;
    private final AlgorithmAlarmMapper algorithmAlarmMapper;
    private final Fh2SdkClient fh2SdkClient;
    private final MissionPlanMapper missionPlanMapper;
    private final MissionExecutionMapper missionExecutionMapper;
    private final QuotaProperties quotaProperties;

    public DashboardSummaryVO summary(String range, LocalDate startDate, LocalDate endDate) {
        PermissionChecker.require("dashboard:read");
        TimeRangeHelper.resolve(range, startDate, endDate);
        Long wsId = workspaceId();

        DashboardSnapshotEntity snapshot = dashboardSnapshotMapper.selectOne(
                new LambdaQueryWrapper<DashboardSnapshotEntity>()
                        .eq(DashboardSnapshotEntity::getWorkspaceId, wsId)
                        .orderByDesc(DashboardSnapshotEntity::getComputedAt)
                        .last("LIMIT 1"));

        Fh2MediaUsage fh2Media = resolveFh2MediaUsage();
        int modelCount = countModels(wsId);
        QuotaVO quota = loadQuota(wsId, fh2Media, modelCount);
        DockStatusVO dockStatus = loadDockStatus(wsId);
        int aircraftCount = countAircraft(wsId);
        int memberCount = countMembers(wsId);
        int waylineCount = countWaylines(wsId);
        int mediaCount = countMedia(wsId, fh2Media);
        int aiAlarmCount = countAlarms();

        AutomationVO automation;
        String computedAt;
        if (snapshot != null) {
            automation = AutomationVO.builder()
                    .workflowCount(nullSafe(snapshot.getWorkflowCount()))
                    .workflowCycles(nullSafe(snapshot.getWorkflowCycles()))
                    .workflowSuccessRate(snapshot.getWorkflowSuccessRate() == null
                            ? 0.0 : snapshot.getWorkflowSuccessRate().doubleValue())
                    .build();
            computedAt = snapshot.getComputedAt() == null ? null : snapshot.getComputedAt().format(FMT);
        } else {
            automation = AutomationVO.builder().workflowCount(0).workflowCycles(0).workflowSuccessRate(0.0).build();
            computedAt = java.time.LocalDateTime.now().format(FMT);
        }

        return DashboardSummaryVO.builder()
                .quota(quota)
                .dockStatus(dockStatus)
                .aircraftCount(aircraftCount)
                .relayCount(0)
                .memberCount(memberCount)
                .waylineCount(waylineCount)
                .algorithmCount(0)
                .mediaCount(mediaCount)
                .modelCount(modelCount)
                .reportCount(0)
                .aiAlarmCount(aiAlarmCount)
                .automation(automation)
                .computedAt(computedAt)
                .build();
    }

    public FlightStatsVO flightStats(String range, LocalDate startDate, LocalDate endDate) {
        PermissionChecker.require("dashboard:read");
        TimeRangeHelper.DateRange dr = TimeRangeHelper.resolve(range, startDate, endDate);
        Long wsId = workspaceId();

        List<MissionExecutionEntity> execs = loadExecutionsInRange(wsId, dr);
        Map<LocalDate, FlightDayAcc> byDay = new HashMap<>();
        int sortie = 0;
        BigDecimal duration = BigDecimal.ZERO;

        for (MissionExecutionEntity e : execs) {
            LocalDate day = effectiveDate(e);
            if (day == null || day.isBefore(dr.start()) || day.isAfter(dr.end())) {
                continue;
            }
            FlightDayAcc acc = byDay.computeIfAbsent(day, d -> new FlightDayAcc());
            acc.sortie++;
            sortie++;
            BigDecimal sec = resolveDurationSec(e);
            if (sec != null) {
                acc.duration = acc.duration.add(sec);
                duration = duration.add(sec);
            }
        }

        List<FlightStatPointVO> series = new ArrayList<>();
        for (LocalDate d = dr.start(); !d.isAfter(dr.end()); d = d.plusDays(1)) {
            FlightDayAcc acc = byDay.getOrDefault(d, FlightDayAcc.EMPTY);
            series.add(FlightStatPointVO.builder()
                    .date(d.format(DATE_FMT))
                    .sortieCount(acc.sortie)
                    .durationSec(acc.duration)
                    .distanceM(BigDecimal.ZERO)
                    .build());
        }

        return FlightStatsVO.builder()
                .sortieCount(sortie)
                .durationSec(duration)
                .distanceM(BigDecimal.ZERO)
                .series(series)
                .build();
    }

    public MissionStatsVO missionStats(String range, LocalDate startDate, LocalDate endDate) {
        PermissionChecker.require("dashboard:read");
        TimeRangeHelper.DateRange dr = TimeRangeHelper.resolve(range, startDate, endDate);
        Long wsId = workspaceId();

        Map<Long, MissionPlanEntity> plans = loadPlans(wsId);
        List<MissionExecutionEntity> execs = loadExecutionsInRange(wsId, dr);

        Map<LocalDate, MissionDayAcc> byDay = new HashMap<>();
        int planned = 0, manual = 0, success = 0, paused = 0, failed = 0;

        for (MissionExecutionEntity e : execs) {
            LocalDate day = effectiveDate(e);
            if (day == null || day.isBefore(dr.start()) || day.isAfter(dr.end())) {
                continue;
            }
            MissionPlanEntity plan = plans.get(e.getPlanId());
            boolean isManual = plan != null && "manual".equalsIgnoreCase(plan.getTaskType());
            MissionDayAcc acc = byDay.computeIfAbsent(day, d -> new MissionDayAcc());
            if (isManual) {
                acc.manual++;
                manual++;
            } else {
                acc.planned++;
                planned++;
            }
            String st = e.getStatus() == null ? "" : e.getStatus().toLowerCase();
            switch (st) {
                case "completed", "success" -> {
                    acc.success++;
                    success++;
                }
                case "paused" -> {
                    acc.paused++;
                    paused++;
                }
                case "failed", "cancelled", "canceled", "ended" -> {
                    acc.failed++;
                    failed++;
                }
                default -> {
                    // pending / running / scheduled 不计入结果分布
                }
            }
        }

        List<MissionStatPointVO> series = new ArrayList<>();
        for (LocalDate d = dr.start(); !d.isAfter(dr.end()); d = d.plusDays(1)) {
            MissionDayAcc acc = byDay.getOrDefault(d, MissionDayAcc.EMPTY);
            series.add(MissionStatPointVO.builder()
                    .date(d.format(DATE_FMT))
                    .plannedCount(acc.planned)
                    .manualCount(acc.manual)
                    .successCount(acc.success)
                    .pausedCount(acc.paused)
                    .failedCount(acc.failed)
                    .build());
        }

        return MissionStatsVO.builder()
                .plannedCount(planned)
                .manualCount(manual)
                .successCount(success)
                .pausedCount(paused)
                .failedCount(failed)
                .series(series)
                .build();
    }

    private QuotaVO loadQuota(Long wsId, Fh2MediaUsage fh2Media, int modelCount) {
        WorkspaceQuotaEntity q = workspaceQuotaMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceQuotaEntity>().eq(WorkspaceQuotaEntity::getWorkspaceId, wsId));
        long used = computeStorageUsedBytes(wsId, fh2Media);
        int mappingUsedDb = q == null || q.getMappingUsed() == null ? 0 : q.getMappingUsed();
        // 建图已用：取库表累加与模型数较大者（重建成功会 +1，司空模型列表更能反映现状）
        int mappingUsed = Math.max(mappingUsedDb, modelCount);

        if (quotaProperties.isPrivate()) {
            long total = quotaProperties.getStorageTotalBytes() == null
                    ? 0L : Math.max(0L, quotaProperties.getStorageTotalBytes());
            return QuotaVO.builder()
                    .mode("private")
                    .storageTotalBytes(total)
                    .storageUsedBytes(used)
                    .mappingQuota(0)
                    .mappingUsed(mappingUsed)
                    // 驾驶舱直播分钟由司空公有云套餐计量；私有化 + 本平台未埋点 → 不展示假数字
                    .livestreamMinutesTotal(null)
                    .livestreamMinutesUsed(null)
                    .build();
        }

        if (q == null) {
            return QuotaVO.builder()
                    .mode("saas")
                    .storageTotalBytes(0L)
                    .storageUsedBytes(used)
                    .mappingQuota(0)
                    .mappingUsed(mappingUsed)
                    .livestreamMinutesTotal(0)
                    .livestreamMinutesUsed(0)
                    .build();
        }
        return QuotaVO.builder()
                .mode("saas")
                .storageTotalBytes(q.getStorageTotalBytes() == null ? 0L : q.getStorageTotalBytes())
                .storageUsedBytes(used)
                .mappingQuota(q.getMappingQuota())
                .mappingUsed(mappingUsed)
                .livestreamMinutesTotal(q.getLivestreamMinutesTotal())
                .livestreamMinutesUsed(q.getLivestreamMinutesUsed())
                .build();
    }

    private long computeStorageUsedBytes(Long wsId, Fh2MediaUsage fh2Media) {
        long localMedia = sumLocalMediaBytes(wsId);
        long localModels = sumLocalModelBytes(wsId);
        long fh2 = fh2Media == null ? 0L : fh2Media.sizeBytes();
        // FH2 可用时媒体以司空为准，本地媒体多为上传旁路；两者相加可能重复，故 FH2 有数据时只加本地模型
        if (fh2Media != null && fh2Media.total() > 0) {
            return fh2 + localModels;
        }
        return localMedia + localModels;
    }

    private long sumLocalMediaBytes(Long wsId) {
        List<MediaAssetEntity> list = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
                .eq(MediaAssetEntity::getWorkspaceId, wsId)
                .eq(MediaAssetEntity::getRecycleStatus, "normal")
                .select(MediaAssetEntity::getId, MediaAssetEntity::getSizeBytes));
        return list.stream().mapToLong(m -> m.getSizeBytes() == null ? 0L : m.getSizeBytes()).sum();
    }

    private long sumLocalModelBytes(Long wsId) {
        List<ModelAssetEntity> list = modelAssetMapper.selectList(new LambdaQueryWrapper<ModelAssetEntity>()
                .eq(ModelAssetEntity::getWorkspaceId, wsId)
                .eq(ModelAssetEntity::getRecycleStatus, "normal")
                .select(ModelAssetEntity::getId, ModelAssetEntity::getSizeBytes));
        return list.stream().mapToLong(m -> m.getSizeBytes() == null ? 0L : m.getSizeBytes()).sum();
    }

    private DockStatusVO loadDockStatus(Long wsId) {
        List<DeviceEntity> docks = deviceMapper.selectList(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getWorkspaceId, wsId)
                .eq(DeviceEntity::getDeviceType, "dock"));
        int idle = 0, working = 0, debugging = 0, offline = 0;
        for (DeviceEntity d : docks) {
            switch (d.getWorkStatus() == null ? "offline" : d.getWorkStatus()) {
                case "idle" -> idle++;
                case "working" -> working++;
                case "debugging" -> debugging++;
                default -> offline++;
            }
        }
        return DockStatusVO.builder().idle(idle).working(working).debugging(debugging).offline(offline).build();
    }

    private int countDevices(Long wsId, String type) {
        Long c = deviceMapper.selectCount(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getWorkspaceId, wsId)
                .eq(DeviceEntity::getDeviceType, type));
        return c == null ? 0 : c.intValue();
    }

    /** 优先司空拓扑（含机场子设备）；本地库常只有 dock 导致首页飞行器为 0 */
    private int countAircraft(Long wsId) {
        if (fh2SdkClient.available()) {
            try {
                List<Fh2DeviceItemVO> aircraft = fh2SdkClient.listAircraft();
                if (aircraft != null && !aircraft.isEmpty()) {
                    return (int) aircraft.stream()
                            .map(Fh2DeviceItemVO::getSn)
                            .filter(sn -> sn != null && !sn.isBlank())
                            .distinct()
                            .count();
                }
            } catch (Exception e) {
                log.debug("[dashboard] listAircraft failed, fallback local: {}", e.getMessage());
            }
        }
        return countDevices(wsId, "aircraft");
    }

    private int countMembers(Long wsId) {
        Long c = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getWorkspaceId, wsId)
                .eq(UserEntity::getStatus, "active"));
        return c == null ? 0 : c.intValue();
    }

    private int countWaylines(Long wsId) {
        Long c = waylineMapper.selectCount(new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getWorkspaceId, wsId));
        return c == null ? 0 : c.intValue();
    }

    private int countMedia(Long wsId, Fh2MediaUsage fh2Media) {
        if (fh2Media != null) {
            return (int) Math.min(Integer.MAX_VALUE, fh2Media.total());
        }
        Long c = mediaAssetMapper.selectCount(new LambdaQueryWrapper<MediaAssetEntity>()
                .eq(MediaAssetEntity::getWorkspaceId, wsId)
                .eq(MediaAssetEntity::getRecycleStatus, "normal"));
        return c == null ? 0 : c.intValue();
    }

    private int countModels(Long wsId) {
        if (fh2SdkClient.available()) {
            try {
                List<Fh2TerraModelVO> page = fh2SdkClient.listTerraModels(1, 1);
                // SDK 未返回 total，尽量扫一页估算；有列表时再拉较大页
                if (page != null && !page.isEmpty()) {
                    List<Fh2TerraModelVO> all = fh2SdkClient.listTerraModels(1, 200);
                    return all == null ? 0 : all.size();
                }
                return 0;
            } catch (Exception e) {
                log.debug("[dashboard] listTerraModels failed, fallback local: {}", e.getMessage());
            }
        }
        Long c = modelAssetMapper.selectCount(new LambdaQueryWrapper<ModelAssetEntity>()
                .eq(ModelAssetEntity::getWorkspaceId, wsId)
                .eq(ModelAssetEntity::getRecycleStatus, "normal"));
        return c == null ? 0 : c.intValue();
    }

    private int countAlarms() {
        Long c = algorithmAlarmMapper.selectCount(new LambdaQueryWrapper<>());
        return c == null ? 0 : c.intValue();
    }

    /**
     * 一次拉取 FH2 媒体 total + 体积（超限则按均值估算），供 summary 复用。
     */
    private Fh2MediaUsage resolveFh2MediaUsage() {
        if (!fh2SdkClient.available()) {
            return null;
        }
        try {
            Fh2MediaPageVO first = fh2SdkClient.listMediaFilesFromIndex(0, FH2_PAGE_SIZE, FH2_MEDIA_TYPES, null);
            long total = first.getTotal();
            List<Fh2MediaFileVO> list = first.getList() == null ? List.of() : first.getList();
            long sum = sumFh2Sizes(list);
            long counted = list.size();

            if (total <= 0) {
                return new Fh2MediaUsage(0, 0L);
            }
            if (total <= FH2_PAGE_SIZE) {
                return new Fh2MediaUsage(total, sum);
            }
            if (total > FH2_SIZE_EXACT_CAP) {
                long avg = counted == 0 ? 0L : sum / counted;
                return new Fh2MediaUsage(total, avg * total);
            }
            for (long start = FH2_PAGE_SIZE; start < total; start += FH2_PAGE_SIZE) {
                long size = Math.min(FH2_PAGE_SIZE, total - start);
                Fh2MediaPageVO page = fh2SdkClient.listMediaFilesFromIndex(start, size, FH2_MEDIA_TYPES, null);
                sum += sumFh2Sizes(page.getList());
            }
            return new Fh2MediaUsage(total, sum);
        } catch (Exception e) {
            log.warn("[dashboard] FH2 media usage failed: {}", e.getMessage());
            return null;
        }
    }

    private static long sumFh2Sizes(List<Fh2MediaFileVO> list) {
        if (list == null || list.isEmpty()) {
            return 0L;
        }
        return list.stream().mapToLong(f -> f.getSizeBytes() == null ? 0L : f.getSizeBytes()).sum();
    }

    private Map<Long, MissionPlanEntity> loadPlans(Long wsId) {
        List<MissionPlanEntity> plans = missionPlanMapper.selectList(
                new LambdaQueryWrapper<MissionPlanEntity>().eq(MissionPlanEntity::getWorkspaceId, wsId));
        return plans.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(MissionPlanEntity::getId, p -> p, (a, b) -> a));
    }

    private List<MissionExecutionEntity> loadExecutionsInRange(Long wsId, TimeRangeHelper.DateRange dr) {
        List<MissionPlanEntity> plans = missionPlanMapper.selectList(
                new LambdaQueryWrapper<MissionPlanEntity>()
                        .eq(MissionPlanEntity::getWorkspaceId, wsId)
                        .select(MissionPlanEntity::getId));
        if (plans.isEmpty()) {
            return List.of();
        }
        List<Long> planIds = plans.stream().map(MissionPlanEntity::getId).filter(Objects::nonNull).toList();
        if (planIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime from = dr.start().atStartOfDay();
        LocalDateTime toExclusive = dr.end().plusDays(1).atStartOfDay();
        return missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .in(MissionExecutionEntity::getPlanId, planIds)
                .apply("COALESCE(actual_start_at, planned_at, created_at) >= {0}"
                                + " AND COALESCE(actual_start_at, planned_at, created_at) < {1}",
                        from, toExclusive));
    }

    private static LocalDate effectiveDate(MissionExecutionEntity e) {
        LocalDateTime t = e.getActualStartAt();
        if (t == null) {
            t = e.getPlannedAt();
        }
        if (t == null) {
            t = e.getCreatedAt();
        }
        return t == null ? null : t.toLocalDate();
    }

    /** 优先库字段；否则用起止时间推算（历史数据未回填时仍可出数） */
    private static BigDecimal resolveDurationSec(MissionExecutionEntity e) {
        if (e.getFlightDurationSec() != null) {
            return e.getFlightDurationSec();
        }
        if (e.getActualStartAt() == null || e.getActualEndAt() == null) {
            return null;
        }
        long sec = java.time.temporal.ChronoUnit.SECONDS.between(e.getActualStartAt(), e.getActualEndAt());
        return BigDecimal.valueOf(Math.max(0, sec));
    }

    private Long workspaceId() {
        LoginUser u = UserContext.get();
        return u == null || u.getWorkspaceId() == null ? 1L : u.getWorkspaceId();
    }

    private int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    private record Fh2MediaUsage(long total, long sizeBytes) {
    }

    private static final class FlightDayAcc {
        static final FlightDayAcc EMPTY = new FlightDayAcc();
        int sortie;
        BigDecimal duration = BigDecimal.ZERO;
    }

    private static final class MissionDayAcc {
        static final MissionDayAcc EMPTY = new MissionDayAcc();
        int planned;
        int manual;
        int success;
        int paused;
        int failed;
    }
}
