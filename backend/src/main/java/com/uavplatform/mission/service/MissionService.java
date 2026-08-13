package com.uavplatform.mission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.algorithm.domain.AlgorithmEntity;
import com.uavplatform.algorithm.mapper.AlgorithmMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.mission.domain.MissionExecutionEntity;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.fh2.dto.Fh2MediaFileVO;
import com.uavplatform.fh2.dto.Fh2MediaPageVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.media.domain.MediaAssetEntity;
import com.uavplatform.media.mapper.MediaAssetMapper;
import com.uavplatform.mission.dto.CreateMissionCmd;
import com.uavplatform.mission.dto.ExecutionMediaVO;
import com.uavplatform.mission.dto.MissionExecutionVO;
import com.uavplatform.mission.dto.MissionPlanDetailVO;
import com.uavplatform.mission.dto.MissionPlanVO;
import com.uavplatform.mission.mapper.MissionExecutionMapper;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.wayline.domain.WaylineContentEntity;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.domain.WaylineSnapshotEntity;
import com.uavplatform.wayline.mapper.WaylineContentMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import com.uavplatform.wayline.service.WaylineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> TASK_TYPES = Set.of("normal", "wayline", "manual");
    private static final Set<String> STRATEGIES = Set.of("immediate", "once", "recurring", "continuous");
    private static final Set<String> FREQ_UNITS = Set.of("day", "week", "month");
    private static final Set<String> DELETABLE = Set.of("draft", "pending", "paused");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MissionPlanMapper missionPlanMapper;
    private final MissionExecutionMapper missionExecutionMapper;
    private final DeviceMapper deviceMapper;
    private final WaylineService waylineService;
    private final WaylineMapper waylineMapper;
    private final WaylineContentMapper waylineContentMapper;
    private final AlgorithmMapper algorithmMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final Fh2SdkClient fh2SdkClient;
    private final MissionDispatchService missionDispatchService;
    private final MissionReconstructService missionReconstructService;

    public PageResult<MissionPlanVO> page(String status, Long deviceId, String keyword, long page, long pageSize) {
        PermissionChecker.require("mission:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        LambdaQueryWrapper<MissionPlanEntity> qw = new LambdaQueryWrapper<MissionPlanEntity>()
                .eq(MissionPlanEntity::getWorkspaceId, workspaceId());
        if (StringUtils.hasText(status)) {
            qw.eq(MissionPlanEntity::getStatus, status);
        }
        if (deviceId != null) {
            qw.eq(MissionPlanEntity::getDeviceId, deviceId);
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(MissionPlanEntity::getName, keyword);
        }
        qw.orderByDesc(MissionPlanEntity::getUpdatedAt);
        Page<MissionPlanEntity> result = missionPlanMapper.selectPage(new Page<>(p, size), qw);
        List<MissionPlanVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    public MissionPlanDetailVO detail(Long id) {
        PermissionChecker.require("mission:read");
        MissionPlanEntity plan = requirePlan(id);
        List<MissionExecutionEntity> rows = missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getPlanId, id)
                .orderByAsc(MissionExecutionEntity::getPlannedAt)
                .orderByAsc(MissionExecutionEntity::getId));
        List<MissionExecutionVO> executions = buildExecutionTimeline(plan, rows);
        attachExecutionMedia(executions);
        missionReconstructService.attachReconstruct(executions);
        int pending = 0, running = 0, completed = 0, failed = 0, cancelled = 0;
        for (MissionExecutionVO item : executions) {
            String st = item.getStatus() == null ? "" : item.getStatus();
            switch (st) {
                case "pending", "scheduled" -> pending++;
                case "running" -> running++;
                case "completed", "success" -> completed++;
                case "failed" -> failed++;
                case "cancelled", "canceled", "ended" -> cancelled++;
                default -> {
                }
            }
        }
        return MissionPlanDetailVO.builder()
                .plan(toVO(plan))
                .executions(executions)
                .totalCount(executions.size())
                .pendingCount(pending)
                .runningCount(running)
                .completedCount(completed)
                .failedCount(failed)
                .cancelledCount(cancelled)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public MissionPlanVO create(CreateMissionCmd cmd) {
        PermissionChecker.require("mission:write");
        validateCreate(cmd);

        DeviceEntity device = resolveDevice(cmd);
        if (device == null || !workspaceId().equals(device.getWorkspaceId())) {
            throw new BizException(ErrorCodes.DEVICE_UNAVAILABLE, "设备不可用");
        }

        Long snapshotId = null;
        if (needsWayline(cmd.getTaskType())) {
            if (cmd.getWaylineId() == null) {
                throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "缺少必填参数");
            }
            WaylineEntity wayline = waylineService.requirePublishedWayline(cmd.getWaylineId());
            WaylineContentEntity content = waylineContentMapper.selectById(wayline.getId());
            WaylineSnapshotEntity snapshot = waylineService.createSnapshot(wayline, content);
            snapshotId = snapshot.getId();
        }

        if (Boolean.TRUE.equals(cmd.getMultimodalEnabled()) && !hasMultimodalAlgorithm()) {
            throw new BizException(ErrorCodes.MULTIMODAL_UNAVAILABLE, "多模态不可用");
        }

        MissionPlanEntity plan = new MissionPlanEntity();
        plan.setWorkspaceId(workspaceId());
        plan.setName(cmd.getName().trim());
        plan.setTaskType(cmd.getTaskType());
        plan.setWaylineId(cmd.getWaylineId());
        plan.setWaylineSnapshotId(snapshotId);
        plan.setDeviceId(device.getId());
        plan.setStrategy(cmd.getStrategy());
        applySchedule(plan, cmd);
        plan.setAccuracyMode(cmd.getAccuracyMode());
        plan.setBreakpointResume(cmd.getBreakpointResume() == null || cmd.getBreakpointResume() ? 1 : 0);
        plan.setSmartRth(cmd.getSmartRth() == null || cmd.getSmartRth() ? 1 : 0);
        plan.setMinFlightHeightM(cmd.getMinFlightHeightM());
        plan.setSignalLostAction(cmd.getSignalLostAction());
        plan.setMultimodalEnabled(Boolean.TRUE.equals(cmd.getMultimodalEnabled()) ? 1 : 0);
        // 详设 U-040：创建后均为 pending；running 仅在真实下发/起飞后由调度或 FH2 状态回写推进
        plan.setStatus("pending");
        plan.setCreatedBy(currentUserId());
        missionPlanMapper.insert(plan);

        if ("immediate".equals(cmd.getStrategy())) {
            // 立即任务：入队待执行（尚未起飞），不写 actualStartAt
            createExecution(plan.getId(), "pending", LocalDateTime.now());
        } else {
            seedScheduledExecutions(plan);
        }

        // 事务提交后再下发司空，避免本地回滚留下远端孤儿任务
        scheduleRemoteDispatchAfterCommit(plan.getId());

        return toVO(plan);
    }

    private void scheduleRemoteDispatchAfterCommit(Long planId) {
        if (planId == null) {
            return;
        }
        Runnable job = () -> {
            try {
                missionDispatchService.dispatchAfterCreate(planId);
            } catch (Exception e) {
                log.error("[mission] remote dispatch after create failed planId={}: {}", planId, e.getMessage(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public MissionPlanVO pause(Long id) {
        PermissionChecker.require("mission:write");
        MissionPlanEntity plan = requirePlan(id);
        if (!"pending".equals(plan.getStatus())) {
            throw new BizException(ErrorCodes.MISSION_STATUS_INVALID, "状态不允许该操作");
        }
        plan.setStatus("paused");
        missionPlanMapper.updateById(plan);
        return toVO(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public MissionPlanVO resume(Long id) {
        PermissionChecker.require("mission:write");
        MissionPlanEntity plan = requirePlan(id);
        if (!"paused".equals(plan.getStatus())) {
            throw new BizException(ErrorCodes.MISSION_STATUS_INVALID, "状态不允许该操作");
        }
        plan.setStatus("pending");
        missionPlanMapper.updateById(plan);
        return toVO(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public MissionPlanVO cancel(Long id) {
        PermissionChecker.require("mission:write");
        MissionPlanEntity plan = requirePlan(id);
        // pending（含立即待执行）/ running / paused 可取消
        if (!Set.of("pending", "running", "paused").contains(plan.getStatus())) {
            throw new BizException(ErrorCodes.MISSION_STATUS_INVALID, "当前状态不可取消");
        }
        plan.setStatus("cancelled");
        missionPlanMapper.updateById(plan);
        // 先尝试删除司空侧任务，再落本地取消态
        try {
            missionDispatchService.cancelRemoteTasks(id);
        } catch (Exception e) {
            log.warn("[mission] cancel remote tasks planId={} fail: {}", id, e.getMessage());
        }
        // 未完成的执行记录一并取消
        List<MissionExecutionEntity> openExecs = missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getPlanId, id)
                .in(MissionExecutionEntity::getStatus, List.of("pending", "running", "scheduled")));
        LocalDateTime now = LocalDateTime.now();
        for (MissionExecutionEntity exec : openExecs) {
            exec.setStatus("cancelled");
            if (exec.getActualEndAt() == null) {
                exec.setActualEndAt(now);
            }
            missionExecutionMapper.updateById(exec);
        }
        return toVO(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public MissionPlanVO copy(Long id) {
        PermissionChecker.require("mission:write");
        MissionPlanEntity source = requirePlan(id);
        MissionPlanEntity copy = new MissionPlanEntity();
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setName(source.getName() + " (copy)");
        copy.setTaskType(source.getTaskType());
        copy.setWaylineId(source.getWaylineId());
        if (source.getWaylineId() != null) {
            try {
                WaylineEntity wayline = waylineService.requireWayline(source.getWaylineId());
                WaylineContentEntity content = waylineContentMapper.selectById(wayline.getId());
                copy.setWaylineSnapshotId(waylineService.createSnapshot(wayline, content).getId());
            } catch (BizException ex) {
                copy.setWaylineSnapshotId(source.getWaylineSnapshotId());
            }
        } else {
            copy.setWaylineSnapshotId(source.getWaylineSnapshotId());
        }
        copy.setDeviceId(source.getDeviceId());
        copy.setStrategy(source.getStrategy());
        copy.setScheduleAt(source.getScheduleAt());
        copy.setCronExpr(source.getCronExpr());
        copy.setAccuracyMode(source.getAccuracyMode());
        copy.setBreakpointResume(source.getBreakpointResume());
        copy.setSmartRth(source.getSmartRth());
        copy.setMinFlightHeightM(source.getMinFlightHeightM());
        copy.setSignalLostAction(source.getSignalLostAction());
        copy.setMultimodalEnabled(source.getMultimodalEnabled());
        copy.setStatus("pending");
        copy.setCreatedBy(currentUserId());
        missionPlanMapper.insert(copy);
        return toVO(copy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("mission:write");
        MissionPlanEntity plan = requirePlan(id);
        if ("running".equals(plan.getStatus())) {
            throw new BizException(ErrorCodes.MISSION_DELETE_FORBIDDEN, "执行中禁止删除");
        }
        // 取消远端尚未结束的司空任务（best-effort，失败不阻断本地删除）
        try {
            missionDispatchService.cancelRemoteTasks(id);
        } catch (Exception e) {
            log.warn("[mission] cancel remote tasks before delete planId={} fail: {}", id, e.getMessage());
        }
        // 软删执行记录
        missionExecutionMapper.delete(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getPlanId, id));
        missionPlanMapper.deleteById(id);
    }

    private void validateCreate(CreateMissionCmd cmd) {
        if (!TASK_TYPES.contains(cmd.getTaskType())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "taskType 非法");
        }
        if (!STRATEGIES.contains(cmd.getStrategy())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "strategy 非法");
        }
        if (cmd.getDeviceId() == null && !StringUtils.hasText(cmd.getDeviceSn())) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "缺少设备参数");
        }
        if (needsWayline(cmd.getTaskType()) && cmd.getWaylineId() == null) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "缺少必填参数");
        }
        if (cmd.getMinFlightHeightM() == null) {
            cmd.setMinFlightHeightM(BigDecimal.valueOf(30));
        }
        if (cmd.getMinFlightHeightM().compareTo(BigDecimal.valueOf(20)) < 0
                || cmd.getMinFlightHeightM().compareTo(BigDecimal.valueOf(1500)) > 0) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "最低航点高度须在 20~1500");
        }
        validateSchedule(cmd);
    }

    private void validateSchedule(CreateMissionCmd cmd) {
        String strategy = cmd.getStrategy();
        if ("immediate".equals(strategy)) {
            return;
        }
        List<CreateMissionCmd.TimeWindow> windows = normalizeWindows(cmd);
        if ("once".equals(strategy)) {
            if (!StringUtils.hasText(cmd.getExecuteDate()) && !StringUtils.hasText(cmd.getScheduleAt())) {
                throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "请选择执行日期");
            }
            if (StringUtils.hasText(cmd.getExecuteDate())) {
                parseDate(cmd.getExecuteDate());
            }
            requireTimeWindows(windows, 1);
            return;
        }
        if (!StringUtils.hasText(cmd.getDateStart()) || !StringUtils.hasText(cmd.getDateEnd())) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "请选择执行日期范围");
        }
        LocalDate start = parseDate(cmd.getDateStart());
        LocalDate end = parseDate(cmd.getDateEnd());
        if (end.isBefore(start)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        requireTimeWindows(windows, 1);
        if (cmd.getFreqInterval() == null || cmd.getFreqInterval() < 1) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "请填写重复频率");
        }
        if (!StringUtils.hasText(cmd.getFreqUnit()) || !FREQ_UNITS.contains(cmd.getFreqUnit())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "重复频率单位非法");
        }
        if ("continuous".equals(strategy)) {
            Integer battery = cmd.getMinBatteryPercent();
            if (battery == null || battery < 1 || battery > 100) {
                throw new BizException(ErrorCodes.BAD_REQUEST, "任务开始电量须在 1~100");
            }
        }
    }

    private void applySchedule(MissionPlanEntity plan, CreateMissionCmd cmd) {
        String strategy = cmd.getStrategy();
        if ("immediate".equals(strategy)) {
            plan.setScheduleAt(null);
            plan.setCronExpr(null);
            plan.setScheduleConfig(null);
            return;
        }
        List<CreateMissionCmd.TimeWindow> windows = normalizeWindows(cmd);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("strategy", strategy);
        config.put("timeWindows", windows.stream().map(w -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("start", w.getStart());
            item.put("end", w.getEnd());
            return item;
        }).collect(Collectors.toList()));

        if ("once".equals(strategy)) {
            String date = StringUtils.hasText(cmd.getExecuteDate())
                    ? cmd.getExecuteDate().trim()
                    : null;
            List<CreateMissionCmd.TimeWindow> onceWindows = new ArrayList<>(windows);
            if (date == null && StringUtils.hasText(cmd.getScheduleAt())) {
                LocalDateTime dt = parseDateTime(cmd.getScheduleAt());
                date = dt.toLocalDate().format(DATE_FMT);
                if (onceWindows.isEmpty()) {
                    CreateMissionCmd.TimeWindow tw = new CreateMissionCmd.TimeWindow();
                    tw.setStart(dt.toLocalTime().format(TIME_FMT));
                    tw.setEnd(dt.toLocalTime().format(TIME_FMT));
                    onceWindows.add(tw);
                }
            }
            if (!StringUtils.hasText(date) || onceWindows.isEmpty()) {
                throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "请完善单次定时信息");
            }
            config.put("executeDate", date);
            config.put("timeWindows", onceWindows.stream().map(w -> {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("start", w.getStart());
                item.put("end", w.getEnd());
                return item;
            }).collect(Collectors.toList()));
            CreateMissionCmd.TimeWindow first = onceWindows.get(0);
            plan.setScheduleAt(LocalDateTime.of(parseDate(date), parseTime(first.getStart())));
            plan.setCronExpr(null);
        } else {
            config.put("dateStart", cmd.getDateStart().trim());
            config.put("dateEnd", cmd.getDateEnd().trim());
            config.put("freqInterval", cmd.getFreqInterval());
            config.put("freqUnit", cmd.getFreqUnit());
            if ("continuous".equals(strategy)) {
                config.put("minBatteryPercent", cmd.getMinBatteryPercent());
            }
            CreateMissionCmd.TimeWindow first = windows.get(0);
            plan.setScheduleAt(LocalDateTime.of(parseDate(cmd.getDateStart()), parseTime(first.getStart())));
            plan.setCronExpr(cmd.getFreqInterval() + "," + cmd.getFreqUnit());
        }

        try {
            plan.setScheduleConfig(JSON.writeValueAsString(config));
        } catch (Exception e) {
            throw new BizException(ErrorCodes.INTERNAL, "调度配置序列化失败");
        }
    }

    private List<CreateMissionCmd.TimeWindow> normalizeWindows(CreateMissionCmd cmd) {
        List<CreateMissionCmd.TimeWindow> out = new ArrayList<>();
        if (CollectionUtils.isEmpty(cmd.getTimeWindows())) {
            return out;
        }
        for (CreateMissionCmd.TimeWindow w : cmd.getTimeWindows()) {
            if (w == null || !StringUtils.hasText(w.getStart()) || !StringUtils.hasText(w.getEnd())) {
                continue;
            }
            CreateMissionCmd.TimeWindow copy = new CreateMissionCmd.TimeWindow();
            copy.setStart(w.getStart().trim());
            copy.setEnd(w.getEnd().trim());
            parseTime(copy.getStart());
            parseTime(copy.getEnd());
            if (parseTime(copy.getEnd()).isBefore(parseTime(copy.getStart()))) {
                throw new BizException(ErrorCodes.BAD_REQUEST, "最晚开始不能早于最早开始");
            }
            out.add(copy);
        }
        return out;
    }

    private void requireTimeWindows(List<CreateMissionCmd.TimeWindow> windows, int min) {
        if (windows == null || windows.size() < min) {
            throw new BizException(ErrorCodes.MISSION_PARAM_MISSING, "请选择执行时间");
        }
    }

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "日期格式须为 yyyy-MM-dd");
        }
    }

    private LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s.trim(), TIME_FMT);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "时间格式须为 HH:mm");
        }
    }

    /** 按本地 ID 或司空机场 SN 解析设备；SN 不存在时落一条本地镜像。 */
    private DeviceEntity resolveDevice(CreateMissionCmd cmd) {
        if (cmd.getDeviceId() != null) {
            DeviceEntity byId = deviceMapper.selectById(cmd.getDeviceId());
            if (byId != null) {
                return byId;
            }
        }
        if (!StringUtils.hasText(cmd.getDeviceSn())) {
            return null;
        }
        String sn = cmd.getDeviceSn().trim();
        DeviceEntity exist = deviceMapper.selectOne(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getWorkspaceId, workspaceId())
                .and(w -> w.eq(DeviceEntity::getSn, sn).or().eq(DeviceEntity::getGatewaySn, sn))
                .last("LIMIT 1"));
        if (exist != null) {
            return exist;
        }
        DeviceEntity created = new DeviceEntity();
        created.setWorkspaceId(workspaceId());
        created.setSn(sn);
        created.setGatewaySn(sn);
        created.setName(sn);
        created.setDeviceType("dock");
        created.setModel("unknown");
        created.setWorkStatus("unknown");
        created.setSource("fh2");
        created.setJoinedAt(LocalDateTime.now());
        deviceMapper.insert(created);
        return created;
    }

    private boolean needsWayline(String taskType) {
        return "normal".equals(taskType) || "wayline".equals(taskType);
    }

    private boolean hasMultimodalAlgorithm() {
        Long count = algorithmMapper.selectCount(new LambdaQueryWrapper<AlgorithmEntity>()
                .eq(AlgorithmEntity::getWorkspaceId, workspaceId())
                .eq(AlgorithmEntity::getStatus, "enabled")
                .in(AlgorithmEntity::getAlgoType, "multimodal", "detection"));
        return count != null && count > 0;
    }

    private void createExecution(Long planId, String status) {
        createExecution(planId, status, LocalDateTime.now());
    }

    private void createExecution(Long planId, String status, LocalDateTime plannedAt) {
        MissionExecutionEntity exec = new MissionExecutionEntity();
        exec.setPlanId(planId);
        exec.setStatus(status);
        exec.setPlannedAt(plannedAt != null ? plannedAt : LocalDateTime.now());
        if ("running".equals(status)) {
            exec.setActualStartAt(LocalDateTime.now());
        }
        missionExecutionMapper.insert(exec);
    }

    /** 按调度配置预生成待执行记录，便于详情页展示未执行槽位。 */
    private void seedScheduledExecutions(MissionPlanEntity plan) {
        List<LocalDateTime> slots = plannedSlotsFromConfig(plan.getScheduleConfig(), plan.getScheduleAt());
        if (slots.isEmpty() && plan.getScheduleAt() != null) {
            slots = List.of(plan.getScheduleAt());
        }
        if (slots.isEmpty()) {
            createExecution(plan.getId(), "pending", LocalDateTime.now());
            return;
        }
        int limit = Math.min(slots.size(), 90);
        for (int i = 0; i < limit; i++) {
            createExecution(plan.getId(), "pending", slots.get(i));
        }
    }

    private List<MissionExecutionVO> buildExecutionTimeline(MissionPlanEntity plan, List<MissionExecutionEntity> rows) {
        List<MissionExecutionVO> list = rows.stream().map(this::toExecutionVO).collect(Collectors.toCollection(ArrayList::new));
        Set<String> occupied = rows.stream()
                .filter(r -> r.getPlannedAt() != null)
                .map(r -> r.getPlannedAt().format(FMT))
                .collect(Collectors.toCollection(HashSet::new));
        for (LocalDateTime slot : plannedSlotsFromConfig(plan.getScheduleConfig(), plan.getScheduleAt())) {
            String key = slot.format(FMT);
            if (occupied.contains(key)) {
                continue;
            }
            // 已有真实记录时，仅补尚未落库的未来槽位提示
            if (slot.isBefore(LocalDateTime.now().minusMinutes(1))) {
                continue;
            }
            list.add(MissionExecutionVO.builder()
                    .id(null)
                    .recordType("planned")
                    .status("scheduled")
                    .plannedAt(key)
                    .build());
            occupied.add(key);
        }
        list.sort(Comparator
                .comparing((MissionExecutionVO v) -> v.getPlannedAt() == null ? "" : v.getPlannedAt())
                .thenComparing(v -> v.getId() == null ? Long.MAX_VALUE : v.getId()));
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<LocalDateTime> plannedSlotsFromConfig(String scheduleConfig, LocalDateTime fallback) {
        List<LocalDateTime> slots = new ArrayList<>();
        if (!StringUtils.hasText(scheduleConfig)) {
            if (fallback != null) {
                slots.add(fallback);
            }
            return slots;
        }
        try {
            Map<String, Object> config = JSON.readValue(scheduleConfig, new TypeReference<Map<String, Object>>() {});
            String strategy = String.valueOf(config.getOrDefault("strategy", ""));
            List<Map<String, String>> windows = (List<Map<String, String>>) config.get("timeWindows");
            if (windows == null || windows.isEmpty()) {
                if (fallback != null) {
                    slots.add(fallback);
                }
                return slots;
            }
            if ("once".equals(strategy)) {
                String date = String.valueOf(config.get("executeDate"));
                String start = windows.get(0).get("start");
                if (StringUtils.hasText(date) && StringUtils.hasText(start)) {
                    slots.add(LocalDateTime.of(LocalDate.parse(date, DATE_FMT), LocalTime.parse(start, TIME_FMT)));
                }
                return slots;
            }
            if (!"recurring".equals(strategy) && !"continuous".equals(strategy)) {
                if (fallback != null) {
                    slots.add(fallback);
                }
                return slots;
            }
            LocalDate startDate = LocalDate.parse(String.valueOf(config.get("dateStart")), DATE_FMT);
            LocalDate endDate = LocalDate.parse(String.valueOf(config.get("dateEnd")), DATE_FMT);
            int interval = Math.max(1, ((Number) config.getOrDefault("freqInterval", 1)).intValue());
            String unit = String.valueOf(config.getOrDefault("freqUnit", "day"));
            LocalDate cursor = startDate;
            int guard = 0;
            while (!cursor.isAfter(endDate) && guard++ < 366) {
                for (Map<String, String> w : windows) {
                    String start = w.get("start");
                    if (!StringUtils.hasText(start)) {
                        continue;
                    }
                    slots.add(LocalDateTime.of(cursor, LocalTime.parse(start, TIME_FMT)));
                    if (slots.size() >= 90) {
                        return slots;
                    }
                }
                cursor = switch (unit) {
                    case "week" -> cursor.plusWeeks(interval);
                    case "month" -> cursor.plusMonths(interval);
                    default -> cursor.plusDays(interval);
                };
            }
        } catch (Exception e) {
            if (fallback != null) {
                slots.add(fallback);
            }
        }
        return slots;
    }

    private MissionExecutionVO toExecutionVO(MissionExecutionEntity e) {
        return MissionExecutionVO.builder()
                .id(e.getId())
                .recordType("execution")
                .status(e.getStatus())
                .plannedAt(e.getPlannedAt() == null ? null : e.getPlannedAt().format(FMT))
                .actualStartAt(e.getActualStartAt() == null ? null : e.getActualStartAt().format(FMT))
                .actualEndAt(e.getActualEndAt() == null ? null : e.getActualEndAt().format(FMT))
                .flightDurationSec(e.getFlightDurationSec())
                .flightDistanceM(e.getFlightDistanceM())
                .failReason(e.getFailReason())
                .fh2TaskUuid(e.getFh2TaskUuid())
                .media(new ArrayList<>())
                .build();
    }

    /**
     * 挂接本条执行的媒体：本地 t_media_asset +（有 fh2TaskUuid 时）司空媒体按 flightId 匹配。
     */
    private void attachExecutionMedia(List<MissionExecutionVO> executions) {
        if (CollectionUtils.isEmpty(executions)) {
            return;
        }
        for (MissionExecutionVO item : executions) {
            if (item.getMedia() == null) {
                item.setMedia(new ArrayList<>());
            }
        }
        List<Long> execIds = executions.stream()
                .map(MissionExecutionVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<ExecutionMediaVO>> localByExec = new HashMap<>();
        if (!execIds.isEmpty()) {
            List<MediaAssetEntity> assets = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
                    .in(MediaAssetEntity::getMissionExecutionId, execIds)
                    .and(w -> w.isNull(MediaAssetEntity::getRecycleStatus)
                            .or()
                            .eq(MediaAssetEntity::getRecycleStatus, "normal"))
                    .orderByDesc(MediaAssetEntity::getId));
            for (MediaAssetEntity asset : assets) {
                localByExec
                        .computeIfAbsent(asset.getMissionExecutionId(), k -> new ArrayList<>())
                        .add(toExecutionMedia(asset));
            }
        }

        Map<String, List<ExecutionMediaVO>> fh2ByFlight = loadFh2MediaByFlightIds(executions);

        for (MissionExecutionVO item : executions) {
            LinkedHashMap<String, ExecutionMediaVO> merged = new LinkedHashMap<>();
            if (item.getId() != null) {
                for (ExecutionMediaVO m : localByExec.getOrDefault(item.getId(), List.of())) {
                    merged.put(mediaKey(m), m);
                }
            }
            if (StringUtils.hasText(item.getFh2TaskUuid())) {
                for (ExecutionMediaVO m : fh2ByFlight.getOrDefault(item.getFh2TaskUuid().trim(), List.of())) {
                    merged.putIfAbsent(mediaKey(m), m);
                }
            }
            item.setMedia(new ArrayList<>(merged.values()));
        }
    }

    /**
     * 删除执行记录下的采集媒体：优先按司空 fileId 删除；若为本平台落盘媒体则同步逻辑删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteExecutionMedia(Long planId, Long executionId, Long mediaId) {
        PermissionChecker.require("mission:write");
        if (planId == null || executionId == null || mediaId == null || mediaId <= 0) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "参数无效");
        }
        MissionPlanEntity plan = missionPlanMapper.selectById(planId);
        if (plan == null || !workspaceId().equals(plan.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MISSION_NOT_FOUND, "飞行计划不存在");
        }
        MissionExecutionEntity exec = missionExecutionMapper.selectById(executionId);
        if (exec == null || !planId.equals(exec.getPlanId())) {
            throw new BizException(ErrorCodes.NOT_FOUND, "执行记录不存在");
        }

        // 本地媒体：挂在该执行上则标记回收
        MediaAssetEntity local = mediaAssetMapper.selectById(mediaId);
        if (local != null && workspaceId().equals(local.getWorkspaceId())
                && executionId.equals(local.getMissionExecutionId())) {
            local.setRecycleStatus("in_trash");
            mediaAssetMapper.updateById(local);
        }

        // 司空侧：fileId 与列表项 id 一致时删除云端文件
        if (fh2SdkClient.available()) {
            try {
                fh2SdkClient.deleteMediaFiles(List.of(mediaId));
            } catch (Exception e) {
                // 仅本地媒体、或司空侧已不存在时：本地已删则视为成功
                if (local != null && executionId.equals(local.getMissionExecutionId())) {
                    log.warn("[mission] FH2 delete media {} skipped: {}", mediaId, e.getMessage());
                    return;
                }
                throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "删除司空媒体失败：" + e.getMessage());
            }
            return;
        }
        if (local == null || !executionId.equals(local.getMissionExecutionId())) {
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "FlightHub SDK 未就绪，无法删除司空媒体");
        }
    }

    /** 通过 FlightHub SDK 按 flightId 取司空媒体（私有化无 OpenAPI），翻页拉全。 */
    private Map<String, List<ExecutionMediaVO>> loadFh2MediaByFlightIds(List<MissionExecutionVO> executions) {
        Set<String> flightIds = executions.stream()
                .map(MissionExecutionVO::getFh2TaskUuid)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (flightIds.isEmpty() || !fh2SdkClient.available()) {
            return Map.of();
        }
        Map<String, List<ExecutionMediaVO>> byFlight = new HashMap<>();
        for (String flightId : flightIds) {
            try {
                List<ExecutionMediaVO> list = new ArrayList<>();
                long pageNo = 1;
                long pageSize = 200;
                long total = Long.MAX_VALUE;
                while ((pageNo - 1) * pageSize < total && pageNo <= 20) {
                    Fh2MediaPageVO page = fh2SdkClient.listMediaFilesByFlightId(
                            flightId, pageNo, pageSize, List.of("2", "3"), null);
                    if (page == null || CollectionUtils.isEmpty(page.getList())) {
                        break;
                    }
                    if (page.getTotal() > 0) {
                        total = page.getTotal();
                    }
                    for (Fh2MediaFileVO file : page.getList()) {
                        if (file == null) {
                            continue;
                        }
                        if (StringUtils.hasText(file.getFlightId())
                                && !flightId.equalsIgnoreCase(file.getFlightId().trim())) {
                            continue;
                        }
                        list.add(toExecutionMediaFromFh2(file));
                    }
                    if (page.getList().size() < pageSize) {
                        break;
                    }
                    pageNo++;
                }
                if (!list.isEmpty()) {
                    byFlight.put(flightId, list);
                }
            } catch (Exception e) {
                log.warn("[mission] SDK media by flight {} failed: {}", flightId, e.getMessage());
            }
        }
        return byFlight;
    }

    private ExecutionMediaVO toExecutionMedia(MediaAssetEntity asset) {
        return ExecutionMediaVO.builder()
                .id(asset.getId())
                .name(asset.getName())
                .mediaType(normalizeMediaType(asset.getMediaType()))
                .previewUrl(null)
                .build();
    }

    private ExecutionMediaVO toExecutionMediaFromFh2(Fh2MediaFileVO file) {
        return ExecutionMediaVO.builder()
                .id(file.getFileId())
                .name(file.getFileName())
                .mediaType(normalizeMediaType(file.getMediaType()))
                .previewUrl(null)
                .build();
    }

    private static String mediaKey(ExecutionMediaVO m) {
        if (StringUtils.hasText(m.getUuid())) {
            return "uuid:" + m.getUuid().trim();
        }
        if (m.getId() != null) {
            return "id:" + m.getId();
        }
        if (StringUtils.hasText(m.getPreviewUrl())) {
            return "preview:" + m.getPreviewUrl().trim();
        }
        return "name:" + (m.getName() == null ? "" : m.getName());
    }

    private static String normalizeMediaType(String mediaType) {
        if (!StringUtils.hasText(mediaType)) {
            return "other";
        }
        String t = mediaType.trim().toLowerCase();
        if (t.startsWith(".")) {
            t = t.substring(1);
        }
        if (t.contains("video") || t.contains("mp4") || t.contains("mov")) {
            return "video";
        }
        if (t.contains("image") || t.contains("photo") || t.contains("jpg")
                || t.contains("jpeg") || t.contains("png") || t.contains("dng") || t.contains("tif")) {
            return "image";
        }
        return t;
    }

    private MissionPlanEntity requirePlan(Long id) {
        MissionPlanEntity plan = missionPlanMapper.selectById(id);
        if (plan == null || !workspaceId().equals(plan.getWorkspaceId())) {
            throw new BizException(ErrorCodes.MISSION_NOT_FOUND, "计划不存在");
        }
        return plan;
    }

    private LocalDateTime parseDateTime(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return LocalDateTime.parse(s, FMT);
    }

    private MissionPlanVO toVO(MissionPlanEntity plan) {
        DeviceEntity device = deviceMapper.selectById(plan.getDeviceId());
        WaylineEntity wayline = plan.getWaylineId() == null ? null : waylineMapper.selectById(plan.getWaylineId());
        return MissionPlanVO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .taskType(plan.getTaskType())
                .waylineId(plan.getWaylineId())
                .waylineSnapshotId(plan.getWaylineSnapshotId())
                .deviceId(plan.getDeviceId())
                .deviceName(device == null ? null : device.getName())
                .waylineName(wayline == null ? null : wayline.getName())
                .strategy(plan.getStrategy())
                .scheduleAt(plan.getScheduleAt() == null ? null : plan.getScheduleAt().format(FMT))
                .cronExpr(plan.getCronExpr())
                .scheduleConfig(plan.getScheduleConfig())
                .accuracyMode(plan.getAccuracyMode())
                .breakpointResume(plan.getBreakpointResume() != null && plan.getBreakpointResume() == 1)
                .smartRth(plan.getSmartRth() != null && plan.getSmartRth() == 1)
                .minFlightHeightM(plan.getMinFlightHeightM())
                .signalLostAction(plan.getSignalLostAction())
                .multimodalEnabled(plan.getMultimodalEnabled() != null && plan.getMultimodalEnabled() == 1)
                .status(plan.getStatus())
                .createdAt(plan.getCreatedAt() == null ? null : plan.getCreatedAt().format(FMT))
                .updatedAt(plan.getUpdatedAt() == null ? null : plan.getUpdatedAt().format(FMT))
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
