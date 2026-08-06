package com.uavplatform.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.analytics.domain.FlightStatDailyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlightStatDailyMapper extends BaseMapper<FlightStatDailyEntity> {
}
