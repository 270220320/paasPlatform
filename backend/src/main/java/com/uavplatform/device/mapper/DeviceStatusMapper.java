package com.uavplatform.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.device.domain.DeviceStatusEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceStatusMapper extends BaseMapper<DeviceStatusEntity> {
}
