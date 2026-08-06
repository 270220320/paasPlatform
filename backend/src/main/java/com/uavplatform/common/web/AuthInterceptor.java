package com.uavplatform.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.security.JwtTokenProvider;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 鉴权拦截器（U-001 logout/me 等需登录接口）。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final TokenBlacklist tokenBlacklist;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
            return false;
        }
        if (tokenBlacklist.contains(token)) {
            writeUnauthorized(response, ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
            return false;
        }
        try {
            Claims claims = jwtTokenProvider.parse(token);
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(Long.valueOf(claims.getSubject()));
            loginUser.setWorkspaceId(claims.get("workspaceId", Long.class));
            loginUser.setRoleCode(claims.get("roleCode", String.class));
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            loginUser.setPermissions(permissions == null ? List.of() : permissions);
            loginUser.setToken(token);
            if (tokenBlacklist.isUserRevoked(loginUser.getUserId())) {
                writeUnauthorized(response, ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
                return false;
            }
            UserContext.set(loginUser);
            return true;
        } catch (ExpiredJwtException ex) {
            writeUnauthorized(response, ErrorCodes.TOKEN_EXPIRED, "Token 已过期");
            return false;
        } catch (Exception ex) {
            writeUnauthorized(response, ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String xToken = request.getHeader("X-User-Token");
        return StringUtils.hasText(xToken) ? xToken.trim() : null;
    }

    private void writeUnauthorized(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(code, message)));
    }
}
