package com.uavplatform.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.device.domain.DeviceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<DeviceEntity> {
}
