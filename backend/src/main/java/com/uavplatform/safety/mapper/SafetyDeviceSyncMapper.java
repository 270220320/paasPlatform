package com.uavplatform.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.safety.domain.SafetyDeviceSyncEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SafetyDeviceSyncMapper extends BaseMapper<SafetyDeviceSyncEntity> {
}
