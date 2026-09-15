package com.uavplatform.notification.controller;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.notification.dto.NotificationVO;
import com.uavplatform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 站内通知（登录即可访问，无需额外权限码）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResult<PageResult<NotificationVO>> page(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(defaultValue = "false") boolean unreadOnly) {
        LoginUser user = requireUser();
        return ApiResult.ok(notificationService.page(user.getWorkspaceId(), user.getUserId(),
                unreadOnly, page, pageSize));
    }

    @GetMapping("/unread-count")
    public ApiResult<Map<String, Long>> unreadCount() {
        LoginUser user = requireUser();
        return ApiResult.ok(Map.of("count", notificationService.unreadCount(
                user.getWorkspaceId(), user.getUserId())));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Boolean> markRead(@PathVariable long id) {
        LoginUser user = requireUser();
        return ApiResult.ok(notificationService.markRead(id, user.getWorkspaceId(), user.getUserId()));
    }

    @PostMapping("/read-all")
    public ApiResult<Integer> markAllRead() {
        LoginUser user = requireUser();
        return ApiResult.ok(notificationService.markAllRead(user.getWorkspaceId(), user.getUserId()));
    }

    /** 通知 SSE 流（fetch 消费，Authorization 鉴权同普通接口） */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        LoginUser user = requireUser();
        return notificationService.openStream(user.getWorkspaceId(), user.getUserId());
    }

    private LoginUser requireUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        return user;
    }
}
