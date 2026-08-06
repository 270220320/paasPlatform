package com.uavplatform.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.algorithm.domain.AlgorithmAlarmEntity;
import com.uavplatform.algorithm.domain.AlgorithmEntity;
import com.uavplatform.algorithm.mapper.AlgorithmAlarmMapper;
import com.uavplatform.algorithm.mapper.AlgorithmMapper;
import com.uavplatform.analytics.domain.DashboardSnapshotEntity;
import com.uavplatform.analytics.domain.FlightStatDailyEntity;
import com.uavplatform.analytics.domain.MissionStatDailyEntity;
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
import com.uavplatform.analytics.mapper.FlightStatDailyMapper;
import com.uavplatform.analytics.mapper.MissionStatDailyMapper;
import com.uavplatform.analytics.mapper.WorkspaceQuotaMapper;
import com.uavplatform.analytics.util.TimeRangeHelper;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.mapper.UserMapper;
import com.uavplatform.media.domain.MediaAssetEntity;
import com.uavplatform.media.domain.ModelAssetEntity;
import com.uavplatform.media.mapper.MediaAssetMapper;
import com.uavplatform.media.mapper.ModelAssetMapper;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.mapper.WaylineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DashboardSnapshotMapper dashboardSnapshotMapper;
    private final FlightStatDailyMapper flightStatDailyMapper;
    private final MissionStatDailyMapper missionStatDailyMapper;
    private final WorkspaceQuotaMapper workspaceQuotaMapper;
    private final UserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final WaylineMapper waylineMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final ModelAssetMapper modelAssetMapper;
    private final AlgorithmMapper algorithmMapper;
    private final AlgorithmAlarmMapper algorithmAlarmMapper;

    public DashboardSummaryVO summary(String range, LocalDate startDate, LocalDate endDate) {
        PermissionChecker.require("dashboard:read");
        TimeRangeHelper.resolve(range, startDate, endDate);
        Long wsId = workspaceId();

        DashboardSnapshotEntity snapshot = dashboardSnapshotMapper.selectOne(
                new LambdaQueryWrapper<DashboardSnapshotEntity>()
                        .eq(DashboardSnapshotEntity::getWorkspaceId, wsId)
                        .orderByDesc(DashboardSnapshotEntity::getComputedAt)
                        .last("LIMIT 1"));

        QuotaVO quota = loadQuota(wsId);
        DockStatusVO dockStatus = loadDockStatus(wsId);
        int aircraftCount = countDevices(wsId, "aircraft");
        int memberCount = countMembers(wsId);
        int waylineCount = countWaylines(wsId);
        int algorithmCount = countEnabledAlgorithms(wsId);
        int mediaCount = countMedia(wsId);
        int modelCount = countModels(wsId);
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
                .algorithmCount(algorithmCount)
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

        List<FlightStatDailyEntity> rows = flightStatDailyMapper.selectList(
                new LambdaQueryWrapper<FlightStatDailyEntity>()
                        .eq(FlightStatDailyEntity::getWorkspaceId, wsId)
                        .ge(FlightStatDailyEntity::getStatDate, dr.start())
                        .le(FlightStatDailyEntity::getStatDate, dr.end())
                        .orderByAsc(FlightStatDailyEntity::getStatDate));

        int sortie = 0;
        BigDecimal duration = BigDecimal.ZERO;
        BigDecimal distance = BigDecimal.ZERO;
        List<FlightStatPointVO> series = rows.stream().map(r -> {
            return FlightStatPointVO.builder()
                    .date(r.getStatDate().format(DATE_FMT))
                    .sortieCount(nullSafe(r.getSortieCount()))
                    .durationSec(r.getDurationSec() == null ? BigDecimal.ZERO : r.getDurationSec())
                    .distanceM(r.getDistanceM() == null ? BigDecimal.ZERO : r.getDistanceM())
                    .build();
        }).collect(Collectors.toList());

        for (FlightStatDailyEntity r : rows) {
            sortie += nullSafe(r.getSortieCount());
            if (r.getDurationSec() != null) {
                duration = duration.add(r.getDurationSec());
            }
            if (r.getDistanceM() != null) {
                distance = distance.add(r.getDistanceM());
            }
        }

        return FlightStatsVO.builder()
                .sortieCount(sortie)
                .durationSec(duration)
                .distanceM(distance)
                .series(series)
                .build();
    }

    public MissionStatsVO missionStats(String range, LocalDate startDate, LocalDate endDate) {
        PermissionChecker.require("dashboard:read");
        TimeRangeHelper.DateRange dr = TimeRangeHelper.resolve(range, startDate, endDate);
        Long wsId = workspaceId();

        List<MissionStatDailyEntity> rows = missionStatDailyMapper.selectList(
                new LambdaQueryWrapper<MissionStatDailyEntity>()
                        .eq(MissionStatDailyEntity::getWorkspaceId, wsId)
                        .ge(MissionStatDailyEntity::getStatDate, dr.start())
                        .le(MissionStatDailyEntity::getStatDate, dr.end())
                        .orderByAsc(MissionStatDailyEntity::getStatDate));

        int planned = 0, manual = 0, success = 0, paused = 0, failed = 0;
        for (MissionStatDailyEntity r : rows) {
            planned += nullSafe(r.getPlannedCount());
            manual += nullSafe(r.getManualCount());
            success += nullSafe(r.getSuccessCount());
            paused += nullSafe(r.getPausedCount());
            failed += nullSafe(r.getFailedCount());
        }

        List<MissionStatPointVO> series = rows.stream()
                .map(r -> MissionStatPointVO.builder()
                        .date(r.getStatDate().format(DATE_FMT))
                        .plannedCount(nullSafe(r.getPlannedCount()))
                        .manualCount(nullSafe(r.getManualCount()))
                        .successCount(nullSafe(r.getSuccessCount()))
                        .pausedCount(nullSafe(r.getPausedCount()))
                        .failedCount(nullSafe(r.getFailedCount()))
                        .build())
                .collect(Collectors.toList());

        return MissionStatsVO.builder()
                .plannedCount(planned)
                .manualCount(manual)
                .successCount(success)
                .pausedCount(paused)
                .failedCount(failed)
                .series(series)
                .build();
    }

    private QuotaVO loadQuota(Long wsId) {
        WorkspaceQuotaEntity q = workspaceQuotaMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceQuotaEntity>().eq(WorkspaceQuotaEntity::getWorkspaceId, wsId));
        if (q == null) {
            return QuotaVO.builder().storageTotalBytes(0L).storageUsedBytes(0L)
                    .mappingQuota(0).mappingUsed(0).livestreamMinutesTotal(0).livestreamMinutesUsed(0).build();
        }
        return QuotaVO.builder()
                .storageTotalBytes(q.getStorageTotalBytes())
                .storageUsedBytes(q.getStorageUsedBytes())
                .mappingQuota(q.getMappingQuota())
                .mappingUsed(q.getMappingUsed())
                .livestreamMinutesTotal(q.getLivestreamMinutesTotal())
                .livestreamMinutesUsed(q.getLivestreamMinutesUsed())
                .build();
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

    private int countEnabledAlgorithms(Long wsId) {
        Long c = algorithmMapper.selectCount(new LambdaQueryWrapper<AlgorithmEntity>()
                .eq(AlgorithmEntity::getWorkspaceId, wsId)
                .eq(AlgorithmEntity::getStatus, "enabled"));
        return c == null ? 0 : c.intValue();
    }

    private int countMedia(Long wsId) {
        Long c = mediaAssetMapper.selectCount(new LambdaQueryWrapper<MediaAssetEntity>()
                .eq(MediaAssetEntity::getWorkspaceId, wsId)
                .eq(MediaAssetEntity::getRecycleStatus, "normal"));
        return c == null ? 0 : c.intValue();
    }

    private int countModels(Long wsId) {
        Long c = modelAssetMapper.selectCount(new LambdaQueryWrapper<ModelAssetEntity>()
                .eq(ModelAssetEntity::getWorkspaceId, wsId)
                .eq(ModelAssetEntity::getRecycleStatus, "normal"));
        return c == null ? 0 : c.intValue();
    }

    private int countAlarms() {
        Long c = algorithmAlarmMapper.selectCount(new LambdaQueryWrapper<>());
        return c == null ? 0 : c.intValue();
    }

    private Long workspaceId() {
        LoginUser u = UserContext.get();
        return u == null || u.getWorkspaceId() == null ? 1L : u.getWorkspaceId();
    }

    private int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }
}
