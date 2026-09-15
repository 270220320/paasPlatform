package com.uavplatform.chat.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.uavplatform.chat.config.ChatProperties;
import com.uavplatform.chat.control.ControlStateManager;
import com.uavplatform.chat.control.DroneControlGateway;
import com.uavplatform.chat.ollama.OllamaClient;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.fh2.dto.Fh2CreateFlightTaskCmd;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 对话控制工具注册表：把平台能力映射为 LLM 可调用工具（含 JSON Schema 与安全分级）。
 *
 * <p>安全分级（对齐大疆 AI Plugin 规范）：
 * <ul>
 *   <li>L0 只读查询 — 直接执行；</li>
 *   <li>L2 常规控制 — 生成待确认动作，前端弹窗确认后执行；</li>
 *   <li>L3 紧急/危险 — 同 L2，且确认时必须填写原因。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    /** 安全等级 */
    public static final int LEVEL_READ = 0;
    public static final int LEVEL_CONTROL = 2;
    public static final int LEVEL_DANGEROUS = 3;

    private final Fh2SdkClient fh2SdkClient;
    private final DroneControlGateway gateway;
    private final ControlStateManager controlState;
    private final ChatProperties properties;
    private final MissionService missionService;

    /** 工具执行上下文（会话设备绑定 + dry-run） */
    public record ToolContext(long sessionId, long userId, String dockSn, String droneSn, boolean dryRun) {
    }

    /** 分发结果：直接执行完成 / 需人工确认 */
    public sealed interface Outcome {
    }

    public record Executed(Object data) implements Outcome {
    }

    public record NeedsConfirmation(PendingAction action) implements Outcome {
    }

    /** 待确认动作（L2/L3） */
    public record PendingAction(String actionId, long sessionId, String tool, int level,
                                Map<String, Object> args, String summary,
                                String messageSnapshot, long createdAtMs) {
        public boolean expired(int ttlMinutes) {
            return System.currentTimeMillis() - createdAtMs > ttlMinutes * 60_000L;
        }
    }

    /** 单个工具定义 */
    public record ToolDef(String name, int level, String description, Map<String, Object> parameters,
                          BiFunction<JsonNode, ToolContext, Object> executor) {
    }

    private final Map<String, ToolDef> tools = new LinkedHashMap<>();

    @jakarta.annotation.PostConstruct
    void init() {
        registerQueries();
        registerControls();
    }

    // ---------------- 分发 ----------------

    /** LLM 工具调用入口：按分级决定直接执行或挂起待确认 */
    public Outcome dispatch(String name, JsonNode args, ToolContext ctx) {
        ToolDef def = tools.get(name);
        if (def == null) {
            return new Executed(Map.of("ok", false, "error", "未知工具：" + name));
        }
        if (def.level() > properties.getMaxSecurityLevel()) {
            return new Executed(Map.of("ok", false,
                    "error", "操作被拒绝：安全等级 L" + def.level() + " 超过平台上限 L" + properties.getMaxSecurityLevel()));
        }
        if (ctx.dryRun() && def.level() > LEVEL_READ) {
            return new Executed(Map.of("ok", true, "dryRun", true,
                    "preview", "（dry-run）将执行 " + name + "，参数：" + args));
        }
        if (def.level() >= LEVEL_CONTROL) {
            String actionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            PendingAction action = new PendingAction(actionId, ctx.sessionId(), name, def.level(),
                    objectMapper().convertValue(args, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    }),
                    summarize(name, args), name + " " + args, System.currentTimeMillis());
            log.info("[chat-control] pending action created id={} tool={} level={} sessionId={} user={}",
                    actionId, name, def.level(), ctx.sessionId(), ctx.userId());
            return new NeedsConfirmation(action);
        }
        return new Executed(safeExecute(def, args, ctx));
    }

    /** 确认后的真正执行（无分级拦截；dry-run 已在确认前拦截） */
    public Object executeNow(String name, JsonNode args, ToolContext ctx) {
        ToolDef def = tools.get(name);
        if (def == null) {
            return Map.of("ok", false, "error", "未知工具：" + name);
        }
        return safeExecute(def, args, ctx);
    }

    public List<Map<String, Object>> ollamaTools() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolDef def : tools.values()) {
            out.add(OllamaClient.toolSchema(def.name(), def.description(), def.parameters()));
        }
        return out;
    }

    public List<Map<String, Object>> toolCatalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolDef def : tools.values()) {
            out.add(Map.of("name", def.name(), "level", def.level(), "description", def.description()));
        }
        return out;
    }

    private Object safeExecute(ToolDef def, JsonNode args, ToolContext ctx) {
        try {
            return def.executor().apply(args, ctx);
        } catch (BizException e) {
            log.warn("[chat-control] tool {} biz error: {}", def.name(), e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        } catch (Exception e) {
            log.error("[chat-control] tool {} error", def.name(), e);
            return Map.of("ok", false, "error", "执行异常：" + e.getMessage());
        }
    }

    private String summarize(String name, JsonNode args) {
        return switch (name) {
            case "takeoff" -> "无人机起飞到 " + args.path("height").asDouble(0) + " 米高度";
            case "flyto" -> "指点飞行到 (" + args.path("latitude").asDouble(0) + ", "
                    + args.path("longitude").asDouble(0) + ") 高度 " + args.path("height").asDouble(0) + " 米";
            case "emergency_stop" -> "紧急停止（原地悬停）";
            case "return_home" -> "自动返航";
            case "return_home_cancel" -> "取消返航";
            case "pause_mission" -> "暂停当前航线任务";
            case "resume_mission" -> "恢复当前航线任务";
            case "take_photo" -> "拍摄一张照片";
            case "flyto_stop" -> "停止指点飞行";
            case "flight_task_create" -> "创建并立即执行航线任务 " + args.path("waylineUuid").asText("");
            default -> name + " " + args;
        };
    }

    // ---------------- 查询类（L0） ----------------

    private void registerQueries() {
        register("device_list", LEVEL_READ,
                "查询工作空间下的机场与无人机设备列表，含名称、SN、类型、在线状态。用户问“有哪些设备/什么在线”时调用。",
                schema(Map.of(),
                        List.of()),
                (args, ctx) -> {
                    gateway.requireSdk();
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (Fh2DeviceItemVO d : fh2SdkClient.listDevices()) {
                        out.add(Map.of(
                                "name", nz(d.getName()),
                                "sn", nz(d.getSn()),
                                "deviceType", nz(d.getDeviceType()),
                                "online", d.isOnline()));
                    }
                    return Map.of("ok", true, "devices", out);
                });

        register("wayline_list", LEVEL_READ,
                "查询可用的司空航线列表（含 waylineUuid、名称、类型）。执行航线任务前先调用以获取 waylineUuid。",
                schema(Map.of(
                        "keyword", prop("string", "按名称过滤（可选）")),
                        List.of()),
                (args, ctx) -> {
                    gateway.requireSdk();
                    String keyword = args.path("keyword").asText("");
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (Fh2WaylineItemVO w : fh2SdkClient.listWaylines(1, 20)) {
                        if (StringUtils.hasText(keyword) && w.getWaylineName() != null
                                && !w.getWaylineName().contains(keyword)) {
                            continue;
                        }
                        out.add(Map.of(
                                "waylineUuid", nz(w.getWaylineUuid()),
                                "name", nz(w.getWaylineName()),
                                "types", w.getWaylineTypes() == null ? List.of() : w.getWaylineTypes()));
                    }
                    return Map.of("ok", true, "waylines", out);
                });

        register("drone_status", LEVEL_READ,
                "查询当前无人机的实时状态：位置（经纬度/高度）、模式码、机头朝向、电量、剩余飞行时间。未指定 droneSn 时使用当前会话绑定的无人机。",
                schema(Map.of(
                        "droneSn", prop("string", "无人机 SN（可选，默认当前选定设备）")),
                        List.of()),
                (args, ctx) -> {
                    gateway.requireSdk();
                    String sn = textOr(args.path("droneSn"), ctx.droneSn());
                    if (!StringUtils.hasText(sn)) {
                        return Map.of("ok", false, "error", "未指定无人机且会话未绑定设备");
                    }
                    var loc = gateway.location(sn);
                    Map<String, Object> battery = null;
                    try {
                        battery = gateway.battery(gateway.requireDrone(sn));
                    } catch (Exception e) {
                        log.debug("[chat-control] battery unavailable: {}", e.getMessage());
                    }
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("ok", true);
                    out.put("droneSn", sn);
                    out.put("flying", loc != null);
                    if (loc != null) {
                        out.put("modeCode", nz(loc.getModeCode()));
                        out.put("latitude", loc.getLatitude());
                        out.put("longitude", loc.getLongitude());
                        out.put("height", loc.getHeight());
                        out.put("heading", loc.getHeading());
                    }
                    out.put("battery", battery);
                    return out;
                });

        register("control_release", LEVEL_READ,
                "释放本会话持有的飞行控制权。控制操作全部完成后应调用。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    if (!controlState.isHeld(ctx.sessionId())) {
                        return Map.of("ok", true, "message", "当前未持有控制权");
                    }
                    controlState.releaseIfHeld(ctx.sessionId());
                    return Map.of("ok", true, "message", "已释放飞行控制权");
                });
    }

    // ---------------- 控制类（L2/L3） ----------------

    private void registerControls() {
        register("takeoff", LEVEL_DANGEROUS,
                "一键起飞：无人机从机场起飞并悬停到指定高度（米，建议 30~120）。latitude/longitude 缺省用无人机当前位置。起飞后可用 flyto 指点飞行。",
                schema(Map.of(
                        "height", propNumber("目标悬停高度（米，相对起飞点，5~150）"),
                        "latitude", propNumber("起飞点纬度（可选，默认当前位置）"),
                        "longitude", propNumber("起飞点经度（可选，默认当前位置）"),
                        "rthAltitude", propNumber("返航高度（米，可选）"),
                        "maxSpeed", propNumber("最大上升速度 m/s（可选）")),
                        List.of("height")),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    double height = clamp(args.path("height").asDouble(50), 5, 150);
                    Double lat = nullableDouble(args.path("latitude"));
                    Double lon = nullableDouble(args.path("longitude"));
                    if (lat == null || lon == null) {
                        var loc = gateway.location(sn);
                        if (loc == null || loc.getLatitude() == null || loc.getLongitude() == null) {
                            throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR,
                                    "无法获取无人机当前位置，请显式传入 latitude/longitude 后再起飞");
                        }
                        lat = loc.getLatitude();
                        lon = loc.getLongitude();
                    }
                    final double fLat = lat;
                    final double fLon = lon;
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.takeoff(gateway.requireDrone(sn), fLat, fLon, height,
                            nullableInt(args.path("rthAltitude")), nullableInt(args.path("maxSpeed")));
                });

        register("flyto", LEVEL_CONTROL,
                "指点飞行：让当前无人机飞到指定 WGS84 经纬度和高度（米）。需无人机已在空中。执行完自动断开 DRC 链路。",
                schema(Map.of(
                        "latitude", propNumber("目标纬度（WGS84）"),
                        "longitude", propNumber("目标经度（WGS84）"),
                        "height", propNumber("目标相对高度（米）"),
                        "speed", propNumber("飞行速度 m/s（可选，默认 10）")),
                        List.of("latitude", "longitude", "height")),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    return controlState.withDrc(ctx.sessionId(), sn, () ->
                            gateway.flyTo(gateway.requireDrone(sn),
                                    args.path("latitude").asDouble(),
                                    args.path("longitude").asDouble(),
                                    clamp(args.path("height").asDouble(50), 2, 500),
                                    nullableInt(args.path("speed"))));
                });

        register("flyto_stop", LEVEL_CONTROL,
                "停止指点飞行：无人机原地悬停。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return controlState.withDrc(ctx.sessionId(), sn, () ->
                            gateway.flyToStop(gateway.requireDrone(sn)));
                });

        register("return_home", LEVEL_CONTROL,
                "自动返航：无人机按返航高度返回起飞点/机场。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.returnHome(gateway.requireDrone(sn));
                });

        register("return_home_cancel", LEVEL_CONTROL,
                "取消返航：中断当前返航动作，无人机原地悬停。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.returnHomeCancel(gateway.requireDrone(sn));
                });

        register("pause_mission", LEVEL_CONTROL,
                "暂停当前正在执行的航线任务（无人机悬停等待）。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.pauseMission(gateway.requireDrone(sn));
                });

        register("resume_mission", LEVEL_CONTROL,
                "恢复已暂停的航线任务。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.resumeMission(gateway.requireDrone(sn));
                });

        register("take_photo", LEVEL_CONTROL,
                "控制无人机相机拍一张照片。",
                schema(Map.of(
                        "cameraKey", prop("string", "镜头标识：wide 广角（默认）/zoom 长焦/ir 红外（可选）")),
                        List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.takePhoto(gateway.requireDrone(sn), args.path("cameraKey").asText(null));
                });

        register("emergency_stop", LEVEL_DANGEROUS,
                "紧急停止：立即中断一切动作并原地悬停（仅危险情况使用，确认时需填写原因）。",
                schema(Map.of(), List.of()),
                (args, ctx) -> {
                    String sn = requireDroneSn(ctx);
                    controlState.ensureControl(ctx.sessionId(), sn);
                    return gateway.emergencyStop(gateway.requireDrone(sn));
                });

        register("flight_task_create", LEVEL_CONTROL,
                "创建并立即执行航线任务：让机场上的无人机按指定航线（waylineUuid，来自 wayline_list）自动飞行。"
                        + "每次执行都必须通过本工具新建任务；历史对话中的 taskUuid 属于已结束旧任务，不可复用。",
                schema(Map.of(
                        "waylineUuid", prop("string", "航线 UUID（先调用 wayline_list 获取）"),
                        "dockSn", prop("string", "起飞机场 SN（可选，默认当前选定机场）"),
                        "rthAltitude", propNumber("返航高度（米，司空侧必填，建议 50~120，缺省传 50）")),
                        List.of("waylineUuid", "rthAltitude")),
                (args, ctx) -> {
                    gateway.requireSdk();
                    String waylineUuid = args.path("waylineUuid").asText("").trim();
                    if (!StringUtils.hasText(waylineUuid)) {
                        throw new BizException(ErrorCodes.BAD_REQUEST, "waylineUuid 不能为空");
                    }
                    String dockSn = textOr(args.path("dockSn"), ctx.dockSn());
                    if (!StringUtils.hasText(dockSn)) {
                        throw new BizException(ErrorCodes.BAD_REQUEST, "未指定机场 SN 且会话未绑定机场");
                    }
                    Double rth = nullableDouble(args.path("rthAltitude"));
                    Fh2CreateFlightTaskCmd cmd = Fh2CreateFlightTaskCmd.builder()
                            .name("chat-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmmss")))
                            .sn(dockSn)
                            .taskType(1)
                            .waylineUuid(waylineUuid)
                            .outOfControlActionInFlight(1)
                            // 司空侧 CreateTaskRequest.RthAltitude 为必填，缺省会报 200614 校验错误
                            .rthAltitude(rth == null ? 50.0 : rth)
                            .build();
                    String uuid = fh2SdkClient.createFlightTask(cmd);
                    // 同步落本地档案（plan+execution）：任务页面可查，媒体按 fh2TaskUuid 自动归组；
                    // 落库失败不影响司空任务本身
                    Long executionId = null;
                    try {
                        var exec = missionService.recordChatFlightTask(
                                cmd.getName(), dockSn, waylineUuid, uuid, ctx.userId());
                        executionId = exec.getId();
                    } catch (Exception rex) {
                        log.warn("[chat-control] record chat flight task locally failed uuid={}: {}",
                                uuid, rex.getMessage(), rex);
                    }
                    return Map.of("ok", true, "taskUuid", uuid,
                            "executionId", executionId == null ? -1L : executionId,
                            "message", "立即任务已创建并下发");
                });
    }

    // ---------------- schema 辅助 ----------------

    private void register(String name, int level, String description, Map<String, Object> parameters,
                          BiFunction<JsonNode, ToolContext, Object> executor) {
        tools.put(name, new ToolDef(name, level, description, parameters, executor));
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        if (!required.isEmpty()) {
            m.put("required", required);
        }
        return m;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> propNumber(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static String requireDroneSn(ToolContext ctx) {
        if (StringUtils.hasText(ctx.droneSn())) {
            return ctx.droneSn();
        }
        throw new BizException(ErrorCodes.BAD_REQUEST, "未指定无人机 SN（请在对话面板选定无人机）");
    }

    private static String textOr(JsonNode node, String fallback) {
        if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
            return node.asText().trim();
        }
        return fallback;
    }

    private static Double nullableDouble(JsonNode node) {
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    private static Integer nullableInt(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
