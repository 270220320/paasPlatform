package com.uavplatform.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.iam.domain.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    @Select("""
            SELECT p.code
            FROM t_permission p
            INNER JOIN t_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId} AND p.deleted = 0
            """)
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);
}
