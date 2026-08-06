package com.uavplatform.mission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.fh2.dto.Fh2CreateFlightTaskCmd;
import com.uavplatform.fh2.dto.Fh2FlightTaskDetailVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.mission.domain.MissionExecutionEntity;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.mission.mapper.MissionExecutionMapper;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.mission.remote.Dji2TaskClient;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.mapper.WaylineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 将本地任务计划下发到司空：
 * <ul>
 *   <li>立即 / 单次：FlightHub Java SDK {@code FlightTaskModule.createFlightTask}</li>
 *   <li>周期 / 连续：dji2 {@code /task/create.rest}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionDispatchService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double DEFAULT_RTH_ALTITUDE_M = 120D;

    /** FH2 TaskDetail.status → 本地 execution 状态 */
    private static final Set<Integer> FH2_PENDING = Set.of(0, 6, 9, 10);
    private static final Set<Integer> FH2_RUNNING = Set.of(2, 3);
    private static final Set<Integer> FH2_SUCCESS = Set.of(5, 8);
    private static final Set<Integer> FH2_FAILED = Set.of(1, 4, 7);

    private final Fh2SdkClient fh2SdkClient;
    private final Dji2TaskClient taskClient;
    private final MissionPlanMapper missionPlanMapper;
    private final MissionExecutionMapper missionExecutionMapper;
    private final DeviceMapper deviceMapper;
    private final WaylineMapper waylineMapper;

    /** 创建事务提交后：按策略下发司空任务 */
    public void dispatchAfterCreate(Long planId) {
        if (planId == null) {
            return;
        }
        MissionPlanEntity plan = missionPlanMapper.selectById(planId);
        if (plan == null || !"pending".equals(plan.getStatus())) {
            return;
        }
        if (!needsRemote(plan.getTaskType())) {
            log.info("[mission-dispatch] manual task skip remote planId={}", planId);
            return;
        }
        try {
            String strategy = plan.getStrategy();
            if ("immediate".equals(strategy) || "once".equals(strategy)) {
                requireSdk();
                List<MissionExecutionEntity> pending = openExecutionsWithoutUuid(planId);
                if (pending.isEmpty()) {
                    return;
                }
                dispatchSdkFlightTask(plan, pending.get(0));
            } else if ("recurring".equals(strategy) || "continuous".equals(strategy)) {
                if (!taskClient.configured()) {
                    throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                            "周期任务需配置 uav.fh2.route-api-base / userToken");
                }
                dispatchCyclePlan(plan);
            }
        } catch (BizException e) {
            markPlanDispatchFailed(plan, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[mission-dispatch] planId={} failed: {}", planId, e.getMessage(), e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            markPlanDispatchFailed(plan, msg);
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "下发司空飞行任务失败：" + msg);
        }
    }

    /** 调度扫描：到期且尚未下发的 pending 执行 */
    public void dispatchDueExecutions() {
        LocalDateTime now = LocalDateTime.now();
        List<MissionExecutionEntity> due = missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getStatus, "pending")
                .isNull(MissionExecutionEntity::getFh2TaskUuid)
                .le(MissionExecutionEntity::getPlannedAt, now.plusMinutes(1))
                .orderByAsc(MissionExecutionEntity::getPlannedAt)
                .last("LIMIT 20"));
        for (MissionExecutionEntity exec : due) {
            MissionPlanEntity plan = missionPlanMapper.selectById(exec.getPlanId());
            if (plan == null || !Set.of("pending", "running").contains(plan.getStatus())) {
                continue;
            }
            if (!needsRemote(plan.getTaskType())) {
                continue;
            }
            if (!"immediate".equals(plan.getStrategy()) && !"once".equals(plan.getStrategy())) {
                continue;
            }
            if (!fh2SdkClient.available()) {
                continue;
            }
            try {
                dispatchSdkFlightTask(plan, exec);
            } catch (Exception e) {
                log.warn("[mission-dispatch] due execId={} fail: {}", exec.getId(), e.getMessage());
                exec.setStatus("failed");
                exec.setFailReason(truncate(e.getMessage()));
                exec.setActualEndAt(LocalDateTime.now());
                missionExecutionMapper.updateById(exec);
                refreshPlanStatus(plan.getId());
            }
        }
    }

    /** 同步已下发、未终态的执行状态 */
    @Transactional(rollbackFor = Exception.class)
    public void syncOpenRemoteStatuses() {
        List<MissionExecutionEntity> open = missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .in(MissionExecutionEntity::getStatus, List.of("pending", "running"))
                .isNotNull(MissionExecutionEntity::getFh2TaskUuid)
                .ne(MissionExecutionEntity::getFh2TaskUuid, "")
                .orderByAsc(MissionExecutionEntity::getId)
                .last("LIMIT 50"));
        Set<Long> touchedPlans = new HashSet<>();
        for (MissionExecutionEntity exec : open) {
            try {
                if (applyRemoteStatus(exec)) {
                    touchedPlans.add(exec.getPlanId());
                }
            } catch (Exception e) {
                log.warn("[mission-sync] execId={} uuid={} fail: {}",
                        exec.getId(), exec.getFh2TaskUuid(), e.getMessage());
            }
        }
        for (Long planId : touchedPlans) {
            refreshPlanStatus(planId);
        }
    }

    /** 取消计划时删除司空侧未完成任务 */
    public void cancelRemoteTasks(Long planId) {
        if (planId == null) {
            return;
        }
        List<MissionExecutionEntity> rows = missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getPlanId, planId)
                .isNotNull(MissionExecutionEntity::getFh2TaskUuid)
                .ne(MissionExecutionEntity::getFh2TaskUuid, ""));
        for (MissionExecutionEntity exec : rows) {
            String uuid = exec.getFh2TaskUuid();
            if (!StringUtils.hasText(uuid)) {
                continue;
            }
            try {
                if (fh2SdkClient.available()) {
                    fh2SdkClient.deleteFlightTask(uuid);
                } else if (taskClient.configured()) {
                    try {
                        taskClient.deleteTaskSub(uuid);
                    } catch (Exception e1) {
                        taskClient.deleteFlightTask(uuid);
                    }
                }
            } catch (Exception e) {
                log.warn("[mission-dispatch] cancel remote uuid={} fail: {}", uuid, e.getMessage());
            }
        }
    }

    /**
     * 对齐 Apifox / SDK：{@code createFlightTask(CreateTaskReq)}。
     * taskType=1 立即；taskType=2 条件（单次窗口）。
     */
    private void dispatchSdkFlightTask(MissionPlanEntity plan, MissionExecutionEntity exec) {
        if (StringUtils.hasText(exec.getFh2TaskUuid())) {
            return;
        }
        DeviceEntity device = requireDevice(plan.getDeviceId());
        WaylineEntity wayline = requireWayline(plan.getWaylineId());
        String dockSn = dockSn(device);
        String waylineUuid = wayline.getFh2WaylineUuid().trim();

        // 对齐 Apifox 示例：只传必填 + 失控动作 + 返航高度，避免多余字段触发 200101
        Fh2CreateFlightTaskCmd.Fh2CreateFlightTaskCmdBuilder builder = Fh2CreateFlightTaskCmd.builder()
                .name(plan.getName())
                .sn(dockSn)
                .timeZone("Asia/Shanghai")
                .waylineUuid(waylineUuid)
                .outOfControlActionInFlight("continue".equalsIgnoreCase(plan.getSignalLostAction()) ? 2 : 1)
                .rthAltitude(resolveRthAltitude(plan));

        if ("immediate".equals(plan.getStrategy())) {
            builder.taskType(1);
        } else {
            builder.taskType(2);
            LocalDateTime begin = exec.getPlannedAt() != null ? exec.getPlannedAt() : LocalDateTime.now();
            LocalDateTime end = resolveOnceEnd(plan, begin);
            long beginEpoch = toEpochSeconds(ensureNotTooSoon(begin));
            long endEpoch = toEpochSeconds(end.isAfter(begin) ? end : begin.plusHours(1));
            if (endEpoch <= beginEpoch) {
                endEpoch = beginEpoch + 3600;
            }
            builder.beginAt(beginEpoch).endAt(endEpoch);
            Integer minBattery = resolveMinBattery(plan);
            if (minBattery != null) {
                builder.minBatteryCapacity(minBattery);
            }
        }

        try {
            String uuid = fh2SdkClient.createFlightTask(builder.build());
            exec.setFh2TaskUuid(uuid);
            missionExecutionMapper.updateById(exec);
            log.info("[mission-dispatch] SDK flight task created planId={} execId={} uuid={}",
                    plan.getId(), exec.getId(), uuid);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "SDK 创建飞行任务失败：" + msg);
        }
    }

    private void requireSdk() {
        if (!fh2SdkClient.available()) {
            String reason = fh2SdkClient.initError().orElse("FlightHub SDK 未就绪");
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                    "无法下发飞行任务：" + reason);
        }
    }

    private void dispatchCyclePlan(MissionPlanEntity plan) {
        DeviceEntity device = requireDevice(plan.getDeviceId());
        WaylineEntity wayline = requireWayline(plan.getWaylineId());
        String dockSn = dockSn(device);
        String waylineUuid = wayline.getFh2WaylineUuid().trim();
        Map<String, Object> config = readConfig(plan.getScheduleConfig());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskName", plan.getName());
        body.put("taskType", 2);
        body.put("routeUuid", waylineUuid);
        body.put("dockSn", dockSn);
        String dateStart = str(config.get("dateStart"));
        String dateEnd = str(config.get("dateEnd"));
        List<Map<String, String>> windows = windowsOf(config);
        String timeStart = windows.isEmpty() ? "00:00" : windows.get(0).getOrDefault("start", "00:00");
        String timeEnd = windows.isEmpty() ? "23:59" : windows.get(0).getOrDefault("end", "23:59");
        body.put("executeDateScope", dateStart + "," + dateEnd);
        body.put("executeTimeScope", timeStart + "," + timeEnd);
        int interval = Math.max(1, number(config.get("freqInterval"), 1));
        int unitCode = freqUnitCode(str(config.get("freqUnit")));
        body.put("executeFrequency", unitCode + "," + interval);

        taskClient.createTaskPlan(body);
        Long remotePlanId = findNewestTaskPlanId(plan.getName(), dockSn, waylineUuid);
        if (remotePlanId == null) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "司空周期计划已创建但未能查询到计划 ID");
        }
        bindCycleSubTasks(plan, remotePlanId);
        log.info("[mission-dispatch] cycle plan created planId={} remoteTaskId={}", plan.getId(), remotePlanId);
    }

    private void bindCycleSubTasks(MissionPlanEntity plan, long remoteTaskId) {
        List<Map<String, Object>> subs = taskClient.listTaskSubs(remoteTaskId, 1, 100);
        if (subs.isEmpty()) {
            return;
        }
        List<MissionExecutionEntity> pending = openExecutionsWithoutUuid(plan.getId());
        pending.sort(Comparator.comparing(e -> e.getPlannedAt() == null ? LocalDateTime.MIN : e.getPlannedAt()));
        List<SubSlot> slots = new ArrayList<>();
        for (Map<String, Object> sub : subs) {
            String uuid = str(sub.get("taskUuid"));
            if (!StringUtils.hasText(uuid)) {
                uuid = str(sub.get("uuid"));
            }
            if (!StringUtils.hasText(uuid)) {
                continue;
            }
            LocalDateTime begin = parseFlexibleDateTime(sub.get("beginDateTime"));
            if (begin == null) {
                begin = parseFlexibleDateTime(sub.get("beginAt"));
            }
            slots.add(new SubSlot(uuid.trim(), begin));
        }
        slots.sort(Comparator.comparing(s -> s.begin == null ? LocalDateTime.MAX : s.begin));
        Set<String> used = new HashSet<>();
        for (MissionExecutionEntity exec : pending) {
            SubSlot matched = null;
            for (SubSlot slot : slots) {
                if (used.contains(slot.uuid)) {
                    continue;
                }
                if (exec.getPlannedAt() != null && slot.begin != null
                        && Math.abs(java.time.Duration.between(exec.getPlannedAt(), slot.begin).toMinutes()) <= 5) {
                    matched = slot;
                    break;
                }
            }
            if (matched == null) {
                for (SubSlot slot : slots) {
                    if (!used.contains(slot.uuid)) {
                        matched = slot;
                        break;
                    }
                }
            }
            if (matched == null) {
                break;
            }
            used.add(matched.uuid);
            exec.setFh2TaskUuid(matched.uuid);
            missionExecutionMapper.updateById(exec);
        }
    }

    private boolean applyRemoteStatus(MissionExecutionEntity exec) {
        Integer statusCode = null;
        Object runAtRaw = null;
        Object completedAtRaw = null;
        Object endAtRaw = null;

        if (fh2SdkClient.available()) {
            try {
                Fh2FlightTaskDetailVO detail = fh2SdkClient.getFlightTaskDetail(exec.getFh2TaskUuid());
                if (detail != null) {
                    statusCode = detail.getStatus();
                    runAtRaw = detail.getRunAt();
                    completedAtRaw = detail.getCompletedAt();
                    endAtRaw = detail.getEndAt();
                }
            } catch (Exception e) {
                log.debug("[mission-sync] SDK detail fail uuid={}: {}", exec.getFh2TaskUuid(), e.getMessage());
            }
        }
        if (statusCode == null && taskClient.configured()) {
            Map<String, Object> detail = taskClient.getFlightTaskDetail(exec.getFh2TaskUuid());
            if (detail.isEmpty()) {
                detail = taskClient.getTaskSubDetail(exec.getFh2TaskUuid());
            }
            if (!detail.isEmpty()) {
                statusCode = toInt(detail.get("status"));
                runAtRaw = detail.get("runAt");
                completedAtRaw = detail.get("completedAt");
                endAtRaw = detail.get("endAt");
            }
        }
        if (statusCode == null) {
            return false;
        }
        String mapped = mapFh2Status(statusCode);
        if (mapped == null || mapped.equals(exec.getStatus())) {
            if ("running".equals(exec.getStatus()) && exec.getActualStartAt() == null) {
                LocalDateTime runAt = parseFlexibleDateTime(runAtRaw);
                if (runAt != null) {
                    exec.setActualStartAt(runAt);
                    missionExecutionMapper.updateById(exec);
                    return true;
                }
            }
            return false;
        }
        exec.setStatus(mapped);
        if (("running".equals(mapped) || "completed".equals(mapped) || "failed".equals(mapped))
                && exec.getActualStartAt() == null) {
            LocalDateTime runAt = parseFlexibleDateTime(runAtRaw);
            exec.setActualStartAt(runAt != null ? runAt : LocalDateTime.now());
        }
        if ("completed".equals(mapped) || "failed".equals(mapped)) {
            LocalDateTime endAt = parseFlexibleDateTime(completedAtRaw);
            if (endAt == null) {
                endAt = parseFlexibleDateTime(endAtRaw);
            }
            exec.setActualEndAt(endAt != null ? endAt : LocalDateTime.now());
            if ("failed".equals(mapped) && !StringUtils.hasText(exec.getFailReason())) {
                exec.setFailReason(fh2StatusLabel(statusCode));
            }
        }
        missionExecutionMapper.updateById(exec);
        return true;
    }

    void refreshPlanStatus(Long planId) {
        MissionPlanEntity plan = missionPlanMapper.selectById(planId);
        if (plan == null || Set.of("cancelled", "paused", "draft").contains(plan.getStatus())) {
            return;
        }
        List<MissionExecutionEntity> rows = missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getPlanId, planId));
        if (rows.isEmpty()) {
            return;
        }
        boolean anyRunning = false;
        boolean anyPending = false;
        boolean anyFailed = false;
        boolean allTerminal = true;
        for (MissionExecutionEntity e : rows) {
            String st = e.getStatus();
            if ("running".equals(st)) {
                anyRunning = true;
                allTerminal = false;
            } else if ("pending".equals(st) || "scheduled".equals(st)) {
                anyPending = true;
                allTerminal = false;
            } else if ("failed".equals(st)) {
                anyFailed = true;
            } else if (!Set.of("completed", "cancelled", "canceled", "ended", "success").contains(st)) {
                allTerminal = false;
            }
        }
        String next;
        if (anyRunning) {
            next = "running";
        } else if (allTerminal) {
            next = anyFailed ? "failed" : "completed";
        } else if (anyPending) {
            next = "pending";
        } else {
            next = plan.getStatus();
        }
        if (!Objects.equals(next, plan.getStatus())) {
            plan.setStatus(next);
            missionPlanMapper.updateById(plan);
        }
    }

    /** 返航高度：优先用最低航点高度，否则默认 120m（对齐 paasVue RETURN_ALTITUDE_DEFAULT） */
    private static double resolveRthAltitude(MissionPlanEntity plan) {
        if (plan.getMinFlightHeightM() != null && plan.getMinFlightHeightM().doubleValue() >= 20) {
            return plan.getMinFlightHeightM().doubleValue();
        }
        return DEFAULT_RTH_ALTITUDE_M;
    }

    private static String fh2StatusLabel(int code) {
        return switch (code) {
            case 1 -> "司空启动失败";
            case 4 -> "司空任务已终止";
            case 7 -> "司空任务超时";
            default -> "司空状态码 " + code;
        };
    }

    private List<MissionExecutionEntity> openExecutionsWithoutUuid(Long planId) {
        return missionExecutionMapper.selectList(new LambdaQueryWrapper<MissionExecutionEntity>()
                .eq(MissionExecutionEntity::getPlanId, planId)
                .in(MissionExecutionEntity::getStatus, List.of("pending", "scheduled"))
                .and(w -> w.isNull(MissionExecutionEntity::getFh2TaskUuid)
                        .or().eq(MissionExecutionEntity::getFh2TaskUuid, ""))
                .orderByAsc(MissionExecutionEntity::getPlannedAt)
                .orderByAsc(MissionExecutionEntity::getId));
    }

    private Long findNewestTaskPlanId(String taskName, String dockSn, String routeUuid) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pageNo", 1);
        query.put("pageCount", 20);
        query.put("taskName", taskName == null ? "" : taskName);
        query.put("dockSn", dockSn);
        query.put("routeUuid", routeUuid);
        List<Map<String, Object>> list = taskClient.listTaskPlans(query);
        Long best = null;
        long bestId = -1;
        for (Map<String, Object> row : list) {
            String name = str(row.get("taskName"));
            if (StringUtils.hasText(taskName) && !taskName.equals(name)) {
                continue;
            }
            String sn = str(row.get("dockSn"));
            if (StringUtils.hasText(dockSn) && StringUtils.hasText(sn) && !dockSn.equals(sn)) {
                continue;
            }
            String route = str(row.get("routeUuid"));
            if (StringUtils.hasText(routeUuid) && StringUtils.hasText(route) && !routeUuid.equals(route)) {
                continue;
            }
            Long id = toLong(row.get("id"));
            if (id != null && id > bestId) {
                bestId = id;
                best = id;
            }
        }
        return best;
    }

    private void markPlanDispatchFailed(MissionPlanEntity plan, String reason) {
        List<MissionExecutionEntity> pending = openExecutionsWithoutUuid(plan.getId());
        LocalDateTime now = LocalDateTime.now();
        for (MissionExecutionEntity exec : pending) {
            exec.setStatus("failed");
            exec.setFailReason(truncate(reason));
            exec.setActualEndAt(now);
            missionExecutionMapper.updateById(exec);
        }
        plan.setStatus("failed");
        missionPlanMapper.updateById(plan);
    }

    private DeviceEntity requireDevice(Long deviceId) {
        DeviceEntity device = deviceId == null ? null : deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BizException(ErrorCodes.DEVICE_UNAVAILABLE, "设备不可用");
        }
        if (!StringUtils.hasText(dockSn(device))) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "设备缺少机场 SN，无法下发");
        }
        return device;
    }

    private WaylineEntity requireWayline(Long waylineId) {
        WaylineEntity wayline = waylineId == null ? null : waylineMapper.selectById(waylineId);
        if (wayline == null || !StringUtils.hasText(wayline.getFh2WaylineUuid())) {
            throw new BizException(ErrorCodes.WAYLINE_UNAVAILABLE, "航线未同步司空 UUID，无法下发飞行任务");
        }
        return wayline;
    }

    private static boolean needsRemote(String taskType) {
        return "normal".equals(taskType) || "wayline".equals(taskType);
    }

    private static String dockSn(DeviceEntity device) {
        if (StringUtils.hasText(device.getGatewaySn())) {
            return device.getGatewaySn().trim();
        }
        return StringUtils.hasText(device.getSn()) ? device.getSn().trim() : null;
    }

    private static String mapFh2Status(int code) {
        if (FH2_PENDING.contains(code)) {
            return "pending";
        }
        if (FH2_RUNNING.contains(code)) {
            return "running";
        }
        if (FH2_SUCCESS.contains(code)) {
            return "completed";
        }
        if (FH2_FAILED.contains(code)) {
            return "failed";
        }
        return null;
    }

    private LocalDateTime resolveOnceEnd(MissionPlanEntity plan, LocalDateTime begin) {
        Map<String, Object> config = readConfig(plan.getScheduleConfig());
        List<Map<String, String>> windows = windowsOf(config);
        String date = str(config.get("executeDate"));
        if (!windows.isEmpty() && StringUtils.hasText(date)) {
            String end = windows.get(0).get("end");
            if (StringUtils.hasText(end)) {
                try {
                    return LocalDateTime.of(LocalDate.parse(date, DATE_FMT), LocalTime.parse(end, TIME_FMT));
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return begin.plusHours(2);
    }

    private Integer resolveMinBattery(MissionPlanEntity plan) {
        Map<String, Object> config = readConfig(plan.getScheduleConfig());
        Object v = config.get("minBatteryPercent");
        if (v == null) {
            return null;
        }
        int n = number(v, 0);
        return n > 0 ? n : null;
    }

    private static LocalDateTime ensureNotTooSoon(LocalDateTime begin) {
        LocalDateTime min = LocalDateTime.now().plusMinutes(2);
        return begin.isBefore(min) ? min : begin;
    }

    private static long toEpochSeconds(LocalDateTime dt) {
        return dt.atZone(ZONE).toEpochSecond();
    }

    private Map<String, Object> readConfig(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return JSON.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> windowsOf(Map<String, Object> config) {
        Object raw = config.get("timeWindows");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, String> copy = new LinkedHashMap<>();
                Object s = m.get("start");
                Object e = m.get("end");
                if (s != null) {
                    copy.put("start", String.valueOf(s));
                }
                if (e != null) {
                    copy.put("end", String.valueOf(e));
                }
                out.add(copy);
            }
        }
        return out;
    }

    private static int freqUnitCode(String unit) {
        if ("week".equalsIgnoreCase(unit)) {
            return 2;
        }
        if ("month".equalsIgnoreCase(unit)) {
            return 3;
        }
        return 1;
    }

    private static LocalDateTime parseFlexibleDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            long v = n.longValue();
            // 秒 / 毫秒
            if (v > 1_000_000_000_000L) {
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(v), ZONE);
            }
            if (v > 1_000_000_000L) {
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(v), ZONE);
            }
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            if (s.matches("^\\d+$")) {
                return parseFlexibleDateTime(Long.parseLong(s));
            }
            if (s.contains("T")) {
                return LocalDateTime.parse(s.replace("Z", "").substring(0, Math.min(19, s.length())));
            }
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (s.length() >= 16) {
                return LocalDateTime.parse(s.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private static int number(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 480 ? msg.substring(0, 480) : msg;
    }

    private record SubSlot(String uuid, LocalDateTime begin) {
    }
}
