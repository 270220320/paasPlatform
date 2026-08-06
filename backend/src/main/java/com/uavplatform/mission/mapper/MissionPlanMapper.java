package com.uavplatform.mission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.mission.domain.MissionPlanEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MissionPlanMapper extends BaseMapper<MissionPlanEntity> {
}
