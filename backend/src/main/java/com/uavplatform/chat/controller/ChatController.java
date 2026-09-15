package com.uavplatform.chat.controller;

import com.uavplatform.chat.agent.AgentToolRegistry;
import com.uavplatform.chat.config.ChatProperties;
import com.uavplatform.chat.domain.ChatMessageEntity;
import com.uavplatform.chat.domain.ChatSessionEntity;
import com.uavplatform.chat.ollama.OllamaClient;
import com.uavplatform.chat.safety.PendingActionStore;
import com.uavplatform.chat.service.ChatAgentService;
import com.uavplatform.chat.service.ChatSessionService;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话控制 API。
 * <ul>
 *   <li>POST /api/v1/chat/sessions/{id}/messages — SSE 对话（事件：thinking/delta/tool_start/tool_result/confirm_required/final/error）</li>
 *   <li>POST /api/v1/chat/actions/{actionId}/confirm|cancel — L2/L3 人工确认</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatProperties properties;
    private final OllamaClient ollama;
    private final AgentToolRegistry registry;
    private final ChatAgentService agentService;
    private final ChatSessionService sessionService;
    private final PendingActionStore pendingActions;
    private final Fh2SdkClient fh2SdkClient;

    /** 面板初始化状态：ollama/SDK 可用性、模型、工具清单、安全上限 */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status() {
        List<String> models = ollama.listModels();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        data.put("ollamaReachable", !models.isEmpty());
        data.put("baseUrl", properties.getBaseUrl());
        data.put("models", models);
        data.put("model", models.isEmpty() ? null : ollama.resolveModel());
        data.put("sdkAvailable", fh2SdkClient.available());
        data.put("maxSecurityLevel", properties.getMaxSecurityLevel());
        data.put("tools", registry.toolCatalog());
        return ApiResult.ok(data);
    }

    @PostMapping("/sessions")
    public ApiResult<ChatSessionEntity> createSession(@RequestBody CreateSessionReq req) {
        ChatSessionEntity s = sessionService.create(UserContext.get().getWorkspaceId(),
                UserContext.get().getUserId(), req.getTitle(), req.getDockSn(), req.getDroneSn());
        return ApiResult.ok(s);
    }

    @GetMapping("/sessions")
    public ApiResult<List<ChatSessionEntity>> listSessions() {
        return ApiResult.ok(sessionService.listSessions(
                UserContext.get().getWorkspaceId(), UserContext.get().getUserId()));
    }

    @GetMapping("/sessions/{id}")
    public ApiResult<ChatSessionEntity> getSession(@PathVariable long id) {
        return ApiResult.ok(sessionService.requireOwned(id, UserContext.get().getUserId()));
    }

    @PostMapping("/sessions/{id}/devices")
    public ApiResult<Void> updateDevices(@PathVariable long id, @RequestBody CreateSessionReq req) {
        sessionService.requireOwned(id, UserContext.get().getUserId());
        sessionService.updateDevices(id, req.getDockSn(), req.getDroneSn());
        return ApiResult.ok(null);
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResult<List<ChatMessageEntity>> messages(@PathVariable long id) {
        sessionService.requireOwned(id, UserContext.get().getUserId());
        return ApiResult.ok(sessionService.listMessages(id));
    }

    @PostMapping("/sessions/{id}/close")
    public ApiResult<Void> closeSession(@PathVariable long id) {
        sessionService.requireOwned(id, UserContext.get().getUserId());
        sessionService.closeSession(id);
        return ApiResult.ok(null);
    }

    /** 删除会话（连同全部消息；若持有飞控控制权则补偿释放） */
    @DeleteMapping("/sessions/{id}")
    public ApiResult<Void> deleteSession(@PathVariable long id) {
        sessionService.requireOwned(id, UserContext.get().getUserId());
        sessionService.deleteSession(id);
        return ApiResult.ok(null);
    }

    /** SSE 对话端点 */
    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@PathVariable long id, @RequestBody AskReq req) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCodes.CHAT_DISABLED, "对话控制功能未开启");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "消息内容不能为空");
        }
        ChatSessionEntity session = sessionService.requireOwned(id, UserContext.get().getUserId());
        return agentService.ask(session, req.getContent().trim(), Boolean.TRUE.equals(req.getDryRun()));
    }

    /** L2/L3 待确认动作查询 */
    @GetMapping("/actions/{actionId}")
    public ApiResult<Map<String, Object>> action(@PathVariable String actionId) {
        var action = pendingActions.get(actionId);
        if (action == null) {
            throw new BizException(ErrorCodes.CHAT_ACTION_NOT_FOUND, "待确认动作不存在或已处理");
        }
        sessionService.requireOwned(action.sessionId(), UserContext.get().getUserId());
        return ApiResult.ok(Map.of(
                "actionId", action.actionId(),
                "tool", action.tool(),
                "level", action.level(),
                "summary", action.summary(),
                "args", action.args()));
    }

    @PostMapping("/actions/{actionId}/confirm")
    public ApiResult<Map<String, Object>> confirm(@PathVariable String actionId, @RequestBody ConfirmReq req) {
        return ApiResult.ok(pendingActions.confirm(actionId, req.getReason()));
    }

    @PostMapping("/actions/{actionId}/cancel")
    public ApiResult<Void> cancel(@PathVariable String actionId) {
        pendingActions.cancel(actionId);
        return ApiResult.ok(null);
    }

    @Data
    public static class CreateSessionReq {
        private String title;
        private String dockSn;
        private String droneSn;
    }

    @Data
    public static class AskReq {
        @NotBlank
        private String content;
        private Boolean dryRun;
    }

    @Data
    public static class ConfirmReq {
        /** L3 危险操作必填 */
        private String reason;
    }
}
