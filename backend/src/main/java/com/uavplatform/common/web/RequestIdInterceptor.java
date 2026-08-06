package com.uavplatform.common.web;

import com.uavplatform.common.result.RequestIdHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 请求 ID 拦截器。
 */
@Component
public class RequestIdInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Request-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        RequestIdHolder.set(requestId);
        response.setHeader(HEADER, requestId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestIdHolder.clear();
    }
}
