package com.uavplatform.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.common.web.TokenBlacklist;
import com.uavplatform.iam.domain.RoleEntity;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.dto.MemberCreateRequest;
import com.uavplatform.iam.dto.MemberUpdateRequest;
import com.uavplatform.iam.dto.MemberVO;
import com.uavplatform.iam.mapper.RoleMapper;
import com.uavplatform.iam.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 人员管理（U-002 / U-004）。
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final String ROLE_SUPER = "super_admin";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklist tokenBlacklist;

    public PageResult<MemberVO> page(Long roleId, String joinMethod, String keyword, long page, long pageSize) {
        PermissionChecker.require("member:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);

        LambdaQueryWrapper<UserEntity> qw = new LambdaQueryWrapper<>();
        if (roleId != null) {
            qw.eq(UserEntity::getRoleId, roleId);
        }
        if (StringUtils.hasText(joinMethod)) {
            qw.eq(UserEntity::getJoinMethod, joinMethod);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(UserEntity::getAccount, keyword).or().like(UserEntity::getDisplayName, keyword));
        }
        qw.orderByDesc(UserEntity::getJoinedAt);

        Page<UserEntity> result = userMapper.selectPage(new Page<>(p, size), qw);
        Map<Long, String> roleNameMap = loadRoleNames(result.getRecords());
        List<MemberVO> list = result.getRecords().stream()
                .map(u -> toVO(u, roleNameMap.get(u.getRoleId())))
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public IdVO create(MemberCreateRequest request) {
        PermissionChecker.require("member:write");
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getAccount, request.getAccount().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCodes.ACCOUNT_EXISTS, "账号已存在");
        }
        RoleEntity role = roleMapper.selectById(request.getRoleId());
        if (role == null) {
            throw new BizException(ErrorCodes.ROLE_NOT_FOUND, "角色不存在");
        }
        if (ROLE_SUPER.equals(role.getCode()) && !PermissionChecker.isSuperAdmin()) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限创建超级管理员");
        }

        LoginUser loginUser = UserContext.get();
        UserEntity entity = new UserEntity();
        entity.setWorkspaceId(loginUser == null || loginUser.getWorkspaceId() == null ? 1L : loginUser.getWorkspaceId());
        entity.setAccount(request.getAccount().trim());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setStatus("active");
        entity.setJoinMethod("organization_add");
        entity.setJoinedAt(LocalDateTime.now());
        entity.setRoleId(role.getId());
        userMapper.insert(entity);
        return new IdVO(entity.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MemberUpdateRequest request) {
        PermissionChecker.require("member:write");
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "人员不存在");
        }
        RoleEntity currentRole = roleMapper.selectById(user.getRoleId());
        boolean targetIsSuper = currentRole != null && ROLE_SUPER.equals(currentRole.getCode());

        if (request.getRoleId() != null && !Objects.equals(request.getRoleId(), user.getRoleId())) {
            RoleEntity newRole = roleMapper.selectById(request.getRoleId());
            if (newRole == null) {
                throw new BizException(ErrorCodes.ROLE_NOT_FOUND, "角色不存在");
            }
            if (targetIsSuper && !ROLE_SUPER.equals(newRole.getCode())) {
                throw new BizException(ErrorCodes.SUPER_ADMIN_PROTECTED, "不可直接降级唯一超级管理员");
            }
            if (ROLE_SUPER.equals(newRole.getCode()) && !PermissionChecker.isSuperAdmin()) {
                throw new BizException(ErrorCodes.FORBIDDEN, "无权限设置为超级管理员");
            }
            user.setRoleId(newRole.getId());
        }
        if (StringUtils.hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            if (!"active".equals(request.getStatus()) && !"disabled".equals(request.getStatus())) {
                throw new BizException(ErrorCodes.BAD_REQUEST, "status 仅支持 active/disabled");
            }
            user.setStatus(request.getStatus());
        }
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("member:write");
        LoginUser loginUser = UserContext.get();
        if (loginUser != null && Objects.equals(loginUser.getUserId(), id)) {
            throw new BizException(ErrorCodes.CANNOT_DELETE_SELF, "不可删除当前登录用户");
        }
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            return;
        }
        RoleEntity role = roleMapper.selectById(user.getRoleId());
        if (role != null && ROLE_SUPER.equals(role.getCode())) {
            Long superCount = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getRoleId, role.getId()));
            if (superCount != null && superCount <= 1) {
                throw new BizException(ErrorCodes.SUPER_ADMIN_PROTECTED, "不可删除唯一超级管理员");
            }
        }
        user.setStatus("disabled");
        userMapper.updateById(user);
        userMapper.deleteById(id);
        tokenBlacklist.revokeUser(id);
    }

    private Map<Long, String> loadRoleNames(List<UserEntity> users) {
        Map<Long, String> map = new HashMap<>();
        for (UserEntity user : users) {
            if (user.getRoleId() == null || map.containsKey(user.getRoleId())) {
                continue;
            }
            RoleEntity role = roleMapper.selectById(user.getRoleId());
            map.put(user.getRoleId(), role == null ? null : role.getName());
        }
        return map;
    }

    private MemberVO toVO(UserEntity user, String roleName) {
        return MemberVO.builder()
                .id(user.getId())
                .account(user.getAccount())
                .displayName(user.getDisplayName())
                .roleId(user.getRoleId())
                .roleName(roleName)
                .joinMethod(user.getJoinMethod())
                .joinedAt(user.getJoinedAt() == null ? null : user.getJoinedAt().format(FMT))
                .status(user.getStatus())
                .build();
    }
}
