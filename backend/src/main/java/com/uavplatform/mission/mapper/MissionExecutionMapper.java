package com.uavplatform.mission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.mission.domain.MissionExecutionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MissionExecutionMapper extends BaseMapper<MissionExecutionEntity> {
}
