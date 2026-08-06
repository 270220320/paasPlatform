package com.uavplatform.iam.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.JwtTokenProvider;
import com.uavplatform.common.web.TokenBlacklist;
import com.uavplatform.iam.domain.RoleEntity;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.dto.LoginRequest;
import com.uavplatform.iam.dto.LoginResponse;
import com.uavplatform.iam.mapper.RoleMapper;
import com.uavplatform.iam.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单测（U-001）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private TokenBlacklist tokenBlacklist;

    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userMapper, roleMapper, passwordEncoder, jwtTokenProvider, tokenBlacklist);
    }

    @Test
    void login_success() {
        UserEntity user = buildUser(passwordEncoder.encode("123456"), "active");
        RoleEntity role = buildRole();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectPermissionCodesByRoleId(1L)).thenReturn(List.of("dashboard:read", "role:manage"));
        when(jwtTokenProvider.createToken(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq("super_admin"), ArgumentMatchers.anyList())).thenReturn("token-demo");
        when(jwtTokenProvider.getExpireSeconds()).thenReturn(86400L);

        LoginRequest request = new LoginRequest();
        request.setAccount("admin@aiflow.local");
        request.setPassword("123456");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("token-demo", response.getAccessToken());
        assertEquals(86400L, response.getExpiresIn());
        assertEquals("admin@aiflow.local", response.getUser().getAccount());
        assertEquals("super_admin", response.getUser().getRoleCode());
        verify(userMapper).updateById(user);
    }

    @Test
    void login_badPassword_shouldThrow10001() {
        UserEntity user = buildUser(passwordEncoder.encode("123456"), "active");
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setAccount("admin@aiflow.local");
        request.setPassword("wrong");

        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals(ErrorCodes.AUTH_BAD_CREDENTIALS, ex.getCode());
    }

    @Test
    void login_disabled_shouldThrow10002() {
        UserEntity user = buildUser(passwordEncoder.encode("123456"), "disabled");
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setAccount("admin@aiflow.local");
        request.setPassword("123456");

        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals(ErrorCodes.AUTH_DISABLED, ex.getCode());
    }

    @Test
    void login_userNotFound_shouldThrow10001() {
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginRequest request = new LoginRequest();
        request.setAccount("nobody@aiflow.local");
        request.setPassword("123456");
        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals(ErrorCodes.AUTH_BAD_CREDENTIALS, ex.getCode());
    }

    private UserEntity buildUser(String hash, String status) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setWorkspaceId(1L);
        user.setAccount("admin@aiflow.local");
        user.setDisplayName("平台超管");
        user.setPasswordHash(hash);
        user.setStatus(status);
        user.setRoleId(1L);
        return user;
    }

    private RoleEntity buildRole() {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setCode("super_admin");
        role.setName("超级管理员");
        return role;
    }
}
