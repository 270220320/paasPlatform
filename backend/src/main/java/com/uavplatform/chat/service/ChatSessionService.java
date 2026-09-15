package com.uavplatform.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.chat.config.ChatProperties;
import com.uavplatform.chat.domain.ChatMessageEntity;
import com.uavplatform.chat.domain.ChatSessionEntity;
import com.uavplatform.chat.mapper.ChatMessageMapper;
import com.uavplatform.chat.mapper.ChatSessionMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.chat.control.ControlStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话会话与消息落库（t_chat_session / t_chat_message）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ControlStateManager controlState;
    private final ChatProperties properties;

    /** 状态时效性敏感的查询类工具：历史结果过期后不可作为事实回答依据 */
    private static final java.util.Set<String> STALE_PRONE_TOOLS = java.util.Set.of(
            "device_list", "wayline_list", "drone_status");

    /** 控制类工具：历史成功结果过期后必须降敏，防止模型抄旧 taskUuid 谎报“已创建/已执行” */
    private static final java.util.Set<String> CONTROL_TOOLS = java.util.Set.of(
            "flight_task_create", "takeoff", "flyto", "flyto_stop", "return_home",
            "return_home_cancel", "pause_mission", "resume_mission", "take_photo", "emergency_stop");

    public ChatSessionEntity create(long workspaceId, long userId, String title, String dockSn, String droneSn) {
        ChatSessionEntity s = new ChatSessionEntity();
        s.setWorkspaceId(workspaceId);
        s.setUserId(userId);
        s.setTitle(StringUtils.hasText(title) ? title.trim() : "新对话");
        s.setDockSn(dockSn);
        s.setDroneSn(droneSn);
        s.setStatus("active");
        s.setCreatedAt(java.time.LocalDateTime.now());
        s.setUpdatedAt(java.time.LocalDateTime.now());
        sessionMapper.insert(s);
        return s;
    }

    public List<ChatSessionEntity> listSessions(long workspaceId, long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getWorkspaceId, workspaceId)
                .eq(ChatSessionEntity::getUserId, userId)
                .orderByDesc(ChatSessionEntity::getUpdatedAt)
                .last("limit 50"));
    }

    public ChatSessionEntity requireOwned(long sessionId, long userId) {
        ChatSessionEntity s = sessionMapper.selectById(sessionId);
        if (s == null || s.getUserId() == null || s.getUserId() != userId) {
            throw new BizException(ErrorCodes.CHAT_SESSION_NOT_FOUND, "会话不存在");
        }
        return s;
    }

    public void updateDevices(long sessionId, String dockSn, String droneSn) {
        ChatSessionEntity s = new ChatSessionEntity();
        s.setId(sessionId);
        s.setDockSn(StringUtils.hasText(dockSn) ? dockSn.trim() : null);
        s.setDroneSn(StringUtils.hasText(droneSn) ? droneSn.trim() : null);
        sessionMapper.updateById(s);
    }

    public List<ChatMessageEntity> listMessages(long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getId));
    }

    public ChatMessageEntity appendUser(long sessionId, String content) {
        return append(sessionId, "user", content, null, null, null, null, null);
    }

    public ChatMessageEntity appendAssistant(long sessionId, String content, String thinking) {
        return append(sessionId, "assistant", content, thinking, null, null, null, null);
    }

    public ChatMessageEntity appendTool(long sessionId, String toolName, String argsJson,
                                        String status, String resultJson) {
        return append(sessionId, "tool", null, null, toolName, argsJson, resultJson, status);
    }

    public void updateToolMessage(long messageId, String status, String resultJson) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.setId(messageId);
        m.setStatus(status);
        m.setToolResult(resultJson);
        messageMapper.updateById(m);
    }

    /**
     * 组装 ollama messages：user/assistant 取文本，tool 取结果摘要（ok/pending/error 均保留）。
     */
    public List<Map<String, Object>> historyForLlm(long sessionId, int window) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        List<ChatMessageEntity> all = listMessages(sessionId);
        int from = Math.max(0, all.size() - window);
        for (ChatMessageEntity m : all.subList(from, all.size())) {
            switch (m.getRole()) {
                case "user" -> out.add(Map.of("role", "user", "content", nz(m.getContent())));
                case "assistant" -> {
                    if (StringUtils.hasText(m.getContent())) {
                        out.add(Map.of("role", "assistant", "content", m.getContent()));
                    }
                }
                case "tool" -> {
                    String result = StringUtils.hasText(m.getToolResult()) ? m.getToolResult()
                            : "{\"status\":\"" + nz(m.getStatus()) + "\"}";
                    if (isStaleQueryResult(m)) {
                        result = "{\"stale\":true,\"message\":\"该查询结果已过期，请重新调用 "
                                + nz(m.getToolName()) + " 获取最新数据\"}";
                    } else if (isStaleControlResult(m)) {
                        result = "{\"stale\":true,\"message\":\"这是历史控制操作的过时结果（仅记录当时已执行），"
                                + "不代表本次已执行；需再次执行必须重新调用 " + nz(m.getToolName())
                                + "，严禁引用其中的 taskUuid 向用户宣称任务已创建\"}";
                    }
                    out.add(toolMessage(m.getToolName(), result));
                }
                default -> {
                }
            }
        }
        return out;
    }

    /** 查询类工具结果超过 TTL 后视为过期：设备/航线/无人机状态实时变化，旧空结果会误导模型不再调工具 */
    private boolean isStaleQueryResult(ChatMessageEntity m) {
        if (!"tool".equals(m.getRole()) || m.getToolName() == null
                || !STALE_PRONE_TOOLS.contains(m.getToolName())
                || !ChatMessageEntity.STATUS_OK.equals(m.getStatus())) {
            return false;
        }
        if (m.getCreatedAt() == null) {
            return false;
        }
        return m.getCreatedAt().isBefore(java.time.LocalDateTime.now()
                .minusMinutes(Math.max(1, properties.getHistoryToolTtlMinutes())));
    }

    /** 控制类工具成功结果超过 TTL 后降敏：旧 taskUuid/执行回执不可再被模型当作可引用事实 */
    private boolean isStaleControlResult(ChatMessageEntity m) {
        if (!"tool".equals(m.getRole()) || m.getToolName() == null
                || !CONTROL_TOOLS.contains(m.getToolName())
                || !(ChatMessageEntity.STATUS_OK.equals(m.getStatus())
                    || ChatMessageEntity.STATUS_CONFIRMED.equals(m.getStatus()))) {
            return false;
        }
        if (m.getCreatedAt() == null) {
            return false;
        }
        return m.getCreatedAt().isBefore(java.time.LocalDateTime.now()
                .minusMinutes(Math.max(1, properties.getHistoryToolTtlMinutes())));
    }

    public static Map<String, Object> toolMessage(String toolName, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("content", content);
        m.put("tool_name", nz(toolName));
        m.put("name", nz(toolName));
        return m;
    }

    /**
     * 首条用户消息自动命名会话（已有用户消息则不动）；必须在 appendUser 之前调用，
     * 否则刚插入的 user 消息会干扰判断。规则与前端一致：压缩空白后截取30字。
     */
    public void autoTitleIfFirst(long sessionId, String content) {
        Long userCount = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(ChatMessageEntity::getRole, "user"));
        if (userCount != null && userCount > 0) {
            return;
        }
        String title = content.replaceAll("\\s+", " ").trim();
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }
        if (title.isEmpty()) {
            return;
        }
        ChatSessionEntity s = new ChatSessionEntity();
        s.setId(sessionId);
        s.setTitle(title);
        sessionMapper.updateById(s);
    }

    /** 删除会话：补偿释放控制权 → 物理删消息 → 逻辑删会话 */
    public void deleteSession(long sessionId) {
        try {
            controlState.releaseIfHeld(sessionId);
        } catch (Exception e) {
            log.warn("[chat] release control on delete session {} failed: {}", sessionId, e.getMessage());
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    /** 关闭会话并补偿释放控制权 */
    public void closeSession(long sessionId) {
        try {
            controlState.releaseIfHeld(sessionId);
        } catch (Exception e) {
            log.warn("[chat] release control on close session {} failed: {}", sessionId, e.getMessage());
        }
        ChatSessionEntity s = new ChatSessionEntity();
        s.setId(sessionId);
        s.setStatus("closed");
        sessionMapper.updateById(s);
    }

    private ChatMessageEntity append(long sessionId, String role, String content, String thinking,
                                     String toolName, String toolArgs, String toolResult, String status) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setThinking(thinking);
        m.setToolName(toolName);
        m.setToolArgs(toolArgs);
        m.setToolResult(toolResult);
        m.setStatus(status);
        messageMapper.insert(m);
        // 触发 session updated_at 变化（排序用）
        ChatSessionEntity touch = new ChatSessionEntity();
        touch.setId(sessionId);
        touch.setUpdatedAt(java.time.LocalDateTime.now());
        sessionMapper.updateById(touch);
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
