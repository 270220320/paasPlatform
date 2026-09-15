package com.uavplatform.chat.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.chat.agent.AgentToolRegistry;
import com.uavplatform.chat.agent.AgentToolRegistry.PendingAction;
import com.uavplatform.chat.config.ChatProperties;
import com.uavplatform.chat.domain.ChatMessageEntity;
import com.uavplatform.chat.service.ChatSessionService;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L2/L3 待确认动作缓存（内存，TTL 由 uav.chat.pending-action-ttl-minutes 控制）。
 * 确认/取消会同步回写 t_chat_message 中对应 tool 消息状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingActionStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentToolRegistry registry;
    private final ChatSessionService sessionService;
    private final ChatProperties properties;

    private record Tracked(PendingAction action, long toolMessageId) {
    }

    private final Map<String, Tracked> store = new ConcurrentHashMap<>();

    public PendingAction put(PendingAction action, long toolMessageId) {
        cleanup();
        store.put(action.actionId(), new Tracked(action, toolMessageId));
        return action;
    }

    public PendingAction get(String actionId) {
        Tracked t = store.get(actionId);
        return t == null ? null : t.action();
    }

    /** 确认并执行（L3 必须提供 reason） */
    public Map<String, Object> confirm(String actionId, String reason) {
        Tracked t = take(actionId);
        PendingAction action = t.action();
        if (action.level() >= AgentToolRegistry.LEVEL_DANGEROUS
                && (reason == null || reason.trim().isEmpty())) {
            store.put(actionId, t);
            throw new BizException(ErrorCodes.BAD_REQUEST, "危险操作必须填写确认原因");
        }
        var session = sessionService.requireOwned(action.sessionId(), UserContext.get().getUserId());
        log.info("[chat-control] action confirmed id={} tool={} user={} reason={}",
                actionId, action.tool(), UserContext.get().getUserId(), reason);
        Map<String, Object> result;
        try {
            Object data = registry.executeNow(action.tool(), toJson(action.args()),
                    new AgentToolRegistry.ToolContext(session.getId(), session.getUserId(),
                            session.getDockSn(), session.getDroneSn(), false));
            result = toMap(data);
            sessionService.updateToolMessage(t.toolMessageId(),
                    ChatMessageEntity.STATUS_CONFIRMED, toJsonString(result));
        } catch (Exception e) {
            log.warn("[chat-control] action confirm execute failed id={} tool={}: {}",
                    actionId, action.tool(), e.getMessage());
            result = Map.of("ok", false, "error", e.getMessage());
            sessionService.updateToolMessage(t.toolMessageId(),
                    ChatMessageEntity.STATUS_ERROR, toJsonString(result));
        }
        return result;
    }

    /** 取消待确认动作 */
    public void cancel(String actionId) {
        Tracked t = store.remove(actionId);
        if (t == null) {
            throw new BizException(ErrorCodes.CHAT_ACTION_NOT_FOUND, "待确认动作不存在或已处理");
        }
        sessionService.updateToolMessage(t.toolMessageId(),
                ChatMessageEntity.STATUS_CANCELLED,
                "{\"ok\":false,\"error\":\"用户取消\"}");
        log.info("[chat-control] action cancelled id={} tool={}", actionId, t.action().tool());
    }

    private Tracked take(String actionId) {
        Tracked t = store.get(actionId);
        if (t == null) {
            throw new BizException(ErrorCodes.CHAT_ACTION_NOT_FOUND, "待确认动作不存在或已处理");
        }
        if (t.action().expired(properties.getPendingActionTtlMinutes())) {
            store.remove(actionId);
            sessionService.updateToolMessage(t.toolMessageId(),
                    ChatMessageEntity.STATUS_CANCELLED,
                    "{\"ok\":false,\"error\":\"确认超时自动取消\"}");
            throw new BizException(ErrorCodes.CHAT_ACTION_NOT_FOUND, "待确认动作已过期，请重新下达指令");
        }
        store.remove(actionId);
        return t;
    }

    private void cleanup() {
        store.values().removeIf(t -> t.action().expired(properties.getPendingActionTtlMinutes()));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode toJson(Map<String, Object> args) {
        return JSON.convertValue(args, com.fasterxml.jackson.databind.node.ObjectNode.class);
    }

    private String toJsonString(Object data) {
        try {
            return JSON.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object data) {
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of("ok", true, "data", String.valueOf(data));
    }
}
