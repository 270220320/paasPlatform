package com.uavplatform.common.web;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token / 用户级失效名单（登出、删人）。
 */
@Component
public class TokenBlacklist {

    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();
    private final Set<Long> revokedUserIds = ConcurrentHashMap.newKeySet();
    /** token -> userId，便于按用户批量失效 */
    private final Map<String, Long> tokenUserMap = new ConcurrentHashMap<>();

    public void remember(String token, Long userId) {
        if (token != null && userId != null) {
            tokenUserMap.put(token, userId);
        }
    }

    public void add(String token) {
        if (token != null && !token.isBlank()) {
            revokedTokens.add(token);
        }
    }

    public void revokeUser(Long userId) {
        if (userId == null) {
            return;
        }
        revokedUserIds.add(userId);
        tokenUserMap.forEach((token, uid) -> {
            if (userId.equals(uid)) {
                revokedTokens.add(token);
            }
        });
    }

    public boolean contains(String token) {
        return token != null && revokedTokens.contains(token);
    }

    public boolean isUserRevoked(Long userId) {
        return userId != null && revokedUserIds.contains(userId);
    }

    /** 登录成功后清除用户失效标记（允许重新登录）。 */
    public void clearUserRevoke(Long userId) {
        if (userId != null) {
            revokedUserIds.remove(userId);
        }
    }

    /** 登录成功后解封 Token：JWT 无随机 jti，同账号重复签发同一字符串，需随登录解除其吊销态。 */
    public void remove(String token) {
        if (token != null) {
            revokedTokens.remove(token);
        }
    }
}
