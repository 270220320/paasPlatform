package com.uavplatform.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.iam.domain.PermissionEntity;
import com.uavplatform.iam.domain.RoleEntity;
import com.uavplatform.iam.dto.PermissionVO;
import com.uavplatform.iam.dto.RoleUpdateRequest;
import com.uavplatform.iam.dto.RoleVO;
import com.uavplatform.iam.mapper.PermissionMapper;
import com.uavplatform.iam.mapper.RoleMapper;
import com.uavplatform.iam.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色权限（U-003）。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public List<RoleVO> listRoles() {
        PermissionChecker.require("role:manage");
        List<RoleEntity> roles = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId));
        return roles.stream().map(this::toSimpleVO).collect(Collectors.toList());
    }

    public RoleVO detail(Long id) {
        PermissionChecker.require("role:manage");
        RoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCodes.ROLE_NOT_FOUND, "角色不存在");
        }
        List<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleId(id);
        List<String> codes = roleMapper.selectPermissionCodesByRoleId(id);
        return RoleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(Objects.equals(role.getIsSystem(), 1))
                .locked(Objects.equals(role.getLocked(), 1))
                .permissionIds(permissionIds == null ? Collections.emptyList() : permissionIds)
                .permissionCodes(codes == null ? Collections.emptyList() : codes)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleUpdateRequest request) {
        PermissionChecker.require("role:manage");
        RoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCodes.ROLE_NOT_FOUND, "角色不存在");
        }
        if (Objects.equals(role.getLocked(), 1)) {
            throw new BizException(ErrorCodes.ROLE_LOCKED, "系统锁定角色不可改权限");
        }
        if (StringUtils.hasText(request.getName())) {
            role.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        roleMapper.updateById(role);

        List<Long> permissionIds = request.getPermissionIds() == null ? Collections.emptyList() : request.getPermissionIds();
        if (!permissionIds.isEmpty()) {
            Long count = permissionMapper.selectCount(new LambdaQueryWrapper<PermissionEntity>()
                    .in(PermissionEntity::getId, permissionIds));
            if (count == null || count != permissionIds.size()) {
                throw new BizException(ErrorCodes.BAD_REQUEST, "存在无效权限点");
            }
        }
        rolePermissionMapper.deleteByRoleId(id);
        for (Long permissionId : permissionIds) {
            rolePermissionMapper.insertRelation(id, permissionId);
        }
    }

    public List<PermissionVO> listPermissions() {
        PermissionChecker.require("role:manage");
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>().orderByAsc(PermissionEntity::getId))
                .stream()
                .map(p -> PermissionVO.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .module(p.getModule())
                        .action(p.getAction())
                        .name(p.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /** 供人员页下拉：只需 member:write 或 member:read */
    public List<RoleVO> listRolesForSelect() {
        if (!PermissionChecker.has("member:read") && !PermissionChecker.has("member:write")
                && !PermissionChecker.has("role:manage")) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限");
        }
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId))
                .stream().map(this::toSimpleVO).collect(Collectors.toList());
    }

    private RoleVO toSimpleVO(RoleEntity role) {
        return RoleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(Objects.equals(role.getIsSystem(), 1))
                .locked(Objects.equals(role.getLocked(), 1))
                .build();
    }
}
