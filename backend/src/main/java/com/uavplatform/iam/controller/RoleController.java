package com.uavplatform.iam.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.iam.dto.PermissionVO;
import com.uavplatform.iam.dto.RoleUpdateRequest;
import com.uavplatform.iam.dto.RoleVO;
import com.uavplatform.iam.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限接口（U-003）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    public ApiResult<List<RoleVO>> listRoles() {
        return ApiResult.ok(roleService.listRoles());
    }

    @GetMapping("/roles/options")
    public ApiResult<List<RoleVO>> roleOptions() {
        return ApiResult.ok(roleService.listRolesForSelect());
    }

    @GetMapping("/roles/{id}")
    public ApiResult<RoleVO> detail(@PathVariable Long id) {
        return ApiResult.ok(roleService.detail(id));
    }

    @PutMapping("/roles/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        roleService.update(id, request);
        return ApiResult.ok(null);
    }

    @GetMapping("/permissions")
    public ApiResult<List<PermissionVO>> permissions() {
        return ApiResult.ok(roleService.listPermissions());
    }
}
