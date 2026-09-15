package com.uavplatform.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.mapper.UserMapper;
import com.uavplatform.notification.domain.NotificationEntity;
import com.uavplatform.notification.dto.NotificationVO;
import com.uavplatform.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 站内通知：事件落库（工作区成员各一份副本）+ SSE 实时推送。
 * <p>publish 全异常兜底：通知失败绝不影响调用方主流程（任务状态同步 / 重建轮询等）。
 */
@Slf4j(topic = "aiflow.notification")
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SSE 会话（按 workspace 推送，emitter 生命周期由回调自清理） */
    private final CopyOnWriteArrayList<StreamSession> sessions = new CopyOnWriteArrayList<>();

    private record StreamSession(SseEmitter emitter, Long workspaceId, Long userId) {
    }

    /** 打开通知流；超时 30 分钟，前端收到 done/超时后自行重连 */
    public SseEmitter openStream(Long workspaceId, Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        StreamSession session = new StreamSession(emitter, workspaceId, userId);
        sessions.add(session);
        emitter.onCompletion(() -> sessions.remove(session));
        emitter.onTimeout(() -> sessions.remove(session));
        emitter.onError(e -> sessions.remove(session));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception e) {
            sessions.remove(session);
        }
        return emitter;
    }

    /** SSE 心跳：防代理/网关空闲断连 */
    @Scheduled(fixedDelayString = "${uav.notification.heartbeat-ms:25000}")
    public void heartbeat() {
        for (StreamSession session : sessions) {
            try {
                session.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                sessions.remove(session);
            }
        }
    }

    /**
     * 发布通知：给工作区全部成员各插一条副本，并向该工作区在线 SSE 会话推送。
     * 任何异常只记日志，不上抛。
     */
    public void publish(Long workspaceId, String type, String level, String title, String content,
                        String refType, Long refId) {
        if (workspaceId == null) {
            return;
        }
        try {
            List<UserEntity> members = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getWorkspaceId, workspaceId)
                    .select(UserEntity::getId));
            if (members == null || members.isEmpty()) {
                log.debug("[notification] workspace={} 无成员，跳过 type={}", workspaceId, type);
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            for (UserEntity member : members) {
                if (member == null || member.getId() == null) {
                    continue;
                }
                NotificationEntity entity = new NotificationEntity();
                entity.setWorkspaceId(workspaceId);
                entity.setUserId(member.getId());
                entity.setType(type);
                entity.setLevel(StringUtils.hasText(level) ? level : "info");
                entity.setTitle(truncate(title, 128));
                entity.setContent(truncate(content, 512));
                entity.setRefType(refType);
                entity.setRefId(refId);
                entity.setCreatedAt(now);
                notificationMapper.insert(entity);
                pushToWorkspace(workspaceId, toVO(entity));
            }
        } catch (Exception e) {
            log.warn("[notification] publish type={} failed: {}", type, e.getMessage());
        }
    }

    /** 分页查询当前用户通知；unreadOnly=true 只看未读 */
    public PageResult<NotificationVO> page(Long workspaceId, Long userId, boolean unreadOnly,
                                           long page, long pageSize) {
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        LambdaQueryWrapper<NotificationEntity> qw = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getWorkspaceId, workspaceId)
                .eq(NotificationEntity::getUserId, userId)
                .orderByDesc(NotificationEntity::getId);
        if (unreadOnly) {
            qw.isNull(NotificationEntity::getReadAt);
        }
        Page<NotificationEntity> result = notificationMapper.selectPage(new Page<>(p, size), qw);
        List<NotificationVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), p, size);
    }

    public long unreadCount(Long workspaceId, Long userId) {
        Long n = notificationMapper.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getWorkspaceId, workspaceId)
                .eq(NotificationEntity::getUserId, userId)
                .isNull(NotificationEntity::getReadAt));
        return n == null ? 0 : n;
    }

    /** 标记单条已读；仅本人的通知可操作 */
    public boolean markRead(long id, Long workspaceId, Long userId) {
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getId, id)
                .eq(NotificationEntity::getWorkspaceId, workspaceId)
                .eq(NotificationEntity::getUserId, userId)
                .isNull(NotificationEntity::getReadAt)
                .set(NotificationEntity::getReadAt, LocalDateTime.now()));
        return updated > 0;
    }

    /** 全部已读 */
    public int markAllRead(Long workspaceId, Long userId) {
        return notificationMapper.update(null, new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getWorkspaceId, workspaceId)
                .eq(NotificationEntity::getUserId, userId)
                .isNull(NotificationEntity::getReadAt)
                .set(NotificationEntity::getReadAt, LocalDateTime.now()));
    }

    private void pushToWorkspace(Long workspaceId, NotificationVO vo) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            return;
        }
        for (StreamSession session : sessions) {
            if (!workspaceId.equals(session.workspaceId())) {
                continue;
            }
            try {
                session.emitter().send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception e) {
                sessions.remove(session);
            }
        }
    }

    private NotificationVO toVO(NotificationEntity entity) {
        return NotificationVO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .level(entity.getLevel())
                .title(entity.getTitle())
                .content(entity.getContent())
                .refType(entity.getRefType())
                .refId(entity.getRefId())
                .read(entity.getReadAt() != null)
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().format(FMT))
                .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
