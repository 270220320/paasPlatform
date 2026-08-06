package com.uavplatform.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.iam.domain.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {
}
