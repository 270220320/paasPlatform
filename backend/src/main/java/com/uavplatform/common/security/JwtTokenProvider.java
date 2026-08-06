package com.uavplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtTokenProvider(
            @Value("${uav.jwt.secret}") String secret,
            @Value("${uav.jwt.expire-seconds:86400}") long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public String createToken(Long userId, Long workspaceId, String roleCode, List<String> permissions) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("workspaceId", workspaceId)
                .claim("roleCode", roleCode)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(exp)
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public boolean isExpired(Exception ex) {
        return ex instanceof ExpiredJwtException;
    }
}
