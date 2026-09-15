package com.uavplatform.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.chat.agent.AgentToolRegistry;
import com.uavplatform.chat.agent.AgentToolRegistry.Executed;
import com.uavplatform.chat.agent.AgentToolRegistry.NeedsConfirmation;
import com.uavplatform.chat.agent.AgentToolRegistry.Outcome;
import com.uavplatform.chat.agent.AgentToolRegistry.ToolContext;
import com.uavplatform.chat.config.ChatProperties;
import com.uavplatform.chat.domain.ChatMessageEntity;
import com.uavplatform.chat.domain.ChatSessionEntity;
import com.uavplatform.chat.ollama.OllamaClient;
import com.uavplatform.chat.safety.PendingActionStore;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 对话 Agent 编排：qwen(Ollama) ↔ 工具执行 ↔ SSE 事件流。
 *
 * <p>事件类型：thinking / delta / tool_start / tool_result / confirm_required / final / error / done。
 * L2/L3 工具挂起等待人工确认后结束本轮（确认执行走 PendingActionStore，不再回流 LLM）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 任务 UUID（8-4-4-4-12）模式：用于幻觉回复硬校验 */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** “任务已创建/已下发”类断言 */
    private static final Pattern TASK_CREATED_CLAIM = Pattern.compile(
            "(已|成功)[^。\\n]{0,10}(创建|下发)|创建并下发|任务已创建");

    /** 明确陈述历史的措辞：属于如实报告历史任务，不判为幻觉 */
    private static final Pattern HISTORICAL_CONTEXT = Pattern.compile(
            "历史|之前|刚才|上次|已结束|旧任务|旧的");

    private final ChatProperties properties;
    private final OllamaClient ollama;
    private final AgentToolRegistry registry;
    private final ChatSessionService sessionService;
    private final PendingActionStore pendingActions;
    private final Fh2SdkClient fh2SdkClient;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "chat-agent");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** SSE 对话入口（异步执行 agent 循环） */
    public SseEmitter ask(ChatSessionEntity session, String content, boolean dryRun) {
        SseEmitter emitter = new SseEmitter(300_000L);
        long userId = UserContext.get().getUserId();
        // 异步线程中 UserContext 已清理，context 快照在此构建
        ToolContext ctx = new ToolContext(session.getId(), userId,
                session.getDockSn(), session.getDroneSn(), dryRun);
        executor.submit(() -> {
            try {
                runAgentLoop(emitter, session, content, ctx);
                emitter.complete();
            } catch (Exception e) {
                log.error("[chat] agent loop failed", e);
                send(emitter, "error", Map.of("message", e.getMessage() == null ? "内部错误" : e.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void runAgentLoop(SseEmitter emitter, ChatSessionEntity session, String content, ToolContext ctx) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCodes.CHAT_DISABLED, "对话控制功能未开启");
        }
        sessionService.autoTitleIfFirst(session.getId(), content);
        sessionService.appendUser(session.getId(), content);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage(session));
        messages.addAll(sessionService.historyForLlm(session.getId(), properties.getHistoryWindow()));

        boolean confirmed = false;
        boolean hallucinationCorrected = false;
        for (int turn = 0; turn < properties.getMaxTurns(); turn++) {
            OllamaClient.Completion completion = ollama.chat(messages, registry.ollamaTools());
            if (StringUtils.hasText(completion.thinking())) {
                send(emitter, "thinking", Map.of("content", completion.thinking()));
            }

            if (!completion.hasToolCalls()) {
                String text = completion.content();
                // 幻觉硬拦截：本轮未调用 flight_task_create（L2 在循环内只会挂起确认，不可能返回 taskUuid），
                // 却宣称任务已创建并引用 taskUuid —— 必然是抄历史结果的编造
                if (isHallucinatedTaskClaim(text)) {
                    log.warn("[chat] hallucinated task-creation reply blocked (session {}): {}",
                            session.getId(), text);
                    if (!hallucinationCorrected) {
                        hallucinationCorrected = true;
                        messages.add(Map.of("role", "assistant", "content", text == null ? "" : text));
                        messages.add(Map.of("role", "user", "content",
                                "【系统校验】你上一条回复声称任务已创建/下发并引用了任务UUID，但本轮并未调用任何工具，"
                                        + "该UUID来自历史对话（旧任务已结束）。禁止编造执行结果："
                                        + "请立即先调用 wayline_list 获取 waylineUuid，再调用 flight_task_create 创建新任务"
                                        + "（rthAltitude 必填，默认 50），或如实告知用户当前无法执行的原因。"));
                        continue;
                    }
                    text = "未能确认新任务已创建：刚才的回复引用了历史任务，实际未发生下发。请重新下达执行航线指令。";
                }
                ChatMessageEntity saved = sessionService.appendAssistant(session.getId(), text, completion.thinking());
                send(emitter, "delta", Map.of("content", text));
                send(emitter, "final", Map.of("content", text, "messageId", saved.getId()));
                return;
            }

            // 记录 assistant 的工具调用意图，便于 ollama 上下文衔接
            messages.add(Map.of("role", "assistant", "content",
                    completion.content() == null ? "" : completion.content()));

            boolean suspended = false;
            for (OllamaClient.ToolCall call : completion.toolCalls()) {
                send(emitter, "tool_start", Map.of("tool", call.name(), "args", call.arguments()));
                Outcome outcome = registry.dispatch(call.name(), call.arguments(), ctx);
                if (outcome instanceof NeedsConfirmation needs) {
                    AgentToolRegistry.PendingAction action = pendingActions.put(
                            needs.action(), savePendingToolMessage(session, call).getId());
                    send(emitter, "confirm_required", Map.of(
                            "actionId", action.actionId(),
                            "tool", action.tool(),
                            "level", action.level(),
                            "summary", action.summary(),
                            "args", action.args(),
                            "reasonRequired", action.level() >= AgentToolRegistry.LEVEL_DANGEROUS));
                    suspended = true;
                    break;
                }
                Executed executed = (Executed) outcome;
                String resultJson = writeJson(executed.data());
                ChatMessageEntity saved = sessionService.appendTool(session.getId(), call.name(),
                        writeJson(call.arguments()), ChatMessageEntity.STATUS_OK, resultJson);
                send(emitter, "tool_result", Map.of(
                        "tool", call.name(), "data", executed.data(), "messageId", saved.getId()));
                messages.add(ChatSessionService.toolMessage(call.name(), resultJson));
            }
            if (suspended) {
                confirmed = true;
                send(emitter, "final", Map.of("content",
                        "操作「" + describeLastConfirm(messages) + "」等待确认，请在面板中确认或取消。"));
                return;
            }
        }
        String text = confirmed ? "本轮操作已达确认节点。"
                : "已达到单轮最大工具调用次数（" + properties.getMaxTurns() + "），请继续提问或简化指令。";
        ChatMessageEntity saved = sessionService.appendAssistant(session.getId(), text, null);
        send(emitter, "final", Map.of("content", text, "messageId", saved.getId()));
    }

    private ChatMessageEntity savePendingToolMessage(ChatSessionEntity session, OllamaClient.ToolCall call) {
        return sessionService.appendTool(session.getId(), call.name(),
                writeJson(call.arguments()), ChatMessageEntity.STATUS_PENDING, null);
    }

    private String describeLastConfirm(List<Map<String, Object>> messages) {
        return "该操作";
    }

    /**
     * 幻觉判定：回复声称任务已创建/下发且引用了 taskUuid，但既无历史陈述措辞、
     * 本轮也不可能拿到新 taskUuid（L2 工具确认前只挂起、确认后不回流 LLM）。
     */
    private static boolean isHallucinatedTaskClaim(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (HISTORICAL_CONTEXT.matcher(text).find()) {
            return false;
        }
        return TASK_CREATED_CLAIM.matcher(text).find() && UUID_PATTERN.matcher(text).find();
    }

    private Map<String, Object> systemMessage(ChatSessionEntity session) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 UAVPlatform 无人机作业平台的对话控制助手，通过调用工具帮用户查询和控制大疆机场（Dock）与无人机。\n");
        sb.append("规则：\n");
        sb.append("1. 当前会话绑定设备：机场SN=").append(nz(session.getDockSn()))
                .append("，无人机SN=").append(nz(session.getDroneSn()))
                .append("。用户未指明设备时使用上述绑定设备。\n");
        sb.append("2. 所有操作必须通过工具执行，不要编造执行结果；未调用工具时严禁向用户声称任何操作已创建/已执行。\n");
        sb.append("3. 坐标为 WGS84 经纬度；高度单位米；速度单位米/秒。\n");
        sb.append("4. 执行航线任务：先用 wayline_list 获取 waylineUuid，再调用 flight_task_create 创建新任务（rthAltitude 必填，缺省用 50）。"
                + "每次执行都必须重新创建；历史对话中的 taskUuid 属于已结束的旧任务，严禁复用或宣称其为本轮创建；"
                + "只有本轮工具返回的 taskUuid 才可告知用户。\n");
        sb.append("5. 危险操作（起飞/急停）会要求用户确认，确认后结果会在下一轮可见，请如实告知用户需要确认。\n");
        sb.append("6. 控制操作完成后建议调用 control_release 释放控制权。\n");
        sb.append("7. 用简洁中文回复，汇报工具返回的真实数据（包括错误）。\n");
        sb.append("8. 设备列表、航线、无人机状态是实时变化的：用户每次询问时必须重新调用对应工具获取当前数据，历史工具结果（尤其标记 stale 的）仅作参考不可引用。\n");
        sb.append("SDK 状态：").append(fh2SdkClient.available() ? "在线" : "离线（控制类工具将失败，需提示用户检查司空服务）").append("。\n");
        if (StringUtils.hasText(properties.getExtraSystemPrompt())) {
            sb.append(properties.getExtraSystemPrompt()).append('\n');
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "system");
        m.put("content", sb.toString());
        return m;
    }

    private void send(SseEmitter emitter, String type, Object data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            if (data instanceof Map<?, ?> map) {
                payload.putAll((Map<String, Object>) map);
            } else {
                payload.put("data", data);
            }
            emitter.send(SseEmitter.event().name("message").data(JSON.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("[chat] sse send {} failed: {}", type, e.getMessage());
        }
    }

    private String writeJson(Object data) {
        try {
            return JSON.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "未绑定" : s;
    }
}
