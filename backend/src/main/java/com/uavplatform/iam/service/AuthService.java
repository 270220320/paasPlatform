package com.uavplatform.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.JwtTokenProvider;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.common.web.TokenBlacklist;
import com.uavplatform.iam.domain.RoleEntity;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.dto.ChangePasswordRequest;
import com.uavplatform.iam.dto.LoginRequest;
import com.uavplatform.iam.dto.LoginResponse;
import com.uavplatform.iam.dto.UpdateEmailRequest;
import com.uavplatform.iam.dto.UserVO;
import com.uavplatform.iam.mapper.RoleMapper;
import com.uavplatform.iam.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 认证服务（U-001）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String STATUS_DISABLED = "disabled";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklist tokenBlacklist;

    /**
     * 账号密码登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getAccount, request.getAccount().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCodes.AUTH_BAD_CREDENTIALS, "账号或密码错误");
        }
        if (STATUS_DISABLED.equalsIgnoreCase(user.getStatus())) {
            throw new BizException(ErrorCodes.AUTH_DISABLED, "账号已禁用");
        }

        RoleEntity role = roleMapper.selectById(user.getRoleId());
        if (role == null) {
            throw new BizException(ErrorCodes.AUTH_BAD_CREDENTIALS, "账号或密码错误");
        }
        List<String> permissions = roleMapper.selectPermissionCodesByRoleId(role.getId());
        if (permissions == null) {
            permissions = Collections.emptyList();
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtTokenProvider.createToken(
                user.getId(),
                user.getWorkspaceId(),
                role.getCode(),
                permissions);
        tokenBlacklist.clearUserRevoke(user.getId());
        tokenBlacklist.remember(token, user.getId());

        UserVO userVO = UserVO.builder()
                .id(user.getId())
                .account(user.getAccount())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .roleCode(role.getCode())
                .roleName(role.getName())
                .permissions(permissions)
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .expiresIn(jwtTokenProvider.getExpireSeconds())
                .user(userVO)
                .build();
    }

    /**
     * 登出：Token 加入黑名单。
     */
    public void logout() {
        LoginUser loginUser = UserContext.get();
        if (loginUser != null && loginUser.getToken() != null) {
            tokenBlacklist.add(loginUser.getToken());
        }
    }

    /**
     * 当前登录用户。
     */
    public UserVO currentUser() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        UserEntity user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        if (STATUS_DISABLED.equalsIgnoreCase(user.getStatus())) {
            throw new BizException(ErrorCodes.AUTH_DISABLED, "账号已禁用");
        }
        RoleEntity role = roleMapper.selectById(user.getRoleId());
        List<String> permissions = role == null
                ? Collections.emptyList()
                : roleMapper.selectPermissionCodesByRoleId(role.getId());
        return UserVO.builder()
                .id(user.getId())
                .account(user.getAccount())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .roleCode(role == null ? loginUser.getRoleCode() : role.getCode())
                .roleName(role == null ? null : role.getName())
                .permissions(permissions == null ? Collections.emptyList() : permissions)
                .build();
    }

    /**
     * 修改当前用户邮箱。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(UpdateEmailRequest request) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        UserEntity user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        user.setEmail(request.getEmail().trim());
        userMapper.updateById(user);
    }

    /**
     * 修改当前用户密码：校验原密码后更新，并吊销所有 Token 强制重新登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        UserEntity user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "未登录或 Token 无效");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCodes.AUTH_PASSWORD_INCORRECT, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        tokenBlacklist.revokeUser(user.getId());
    }
}
