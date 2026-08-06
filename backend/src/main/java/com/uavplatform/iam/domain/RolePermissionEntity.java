package com.uavplatform.iam.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色权限关联。
 */
@Data
@TableName("t_role_permission")
public class RolePermissionEntity {

    private Long roleId;
    private Long permissionId;
}
