package com.uavplatform.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.iam.domain.RolePermissionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    @Delete("DELETE FROM t_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT permission_id FROM t_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO t_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permissionId})")
    int insertRelation(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
