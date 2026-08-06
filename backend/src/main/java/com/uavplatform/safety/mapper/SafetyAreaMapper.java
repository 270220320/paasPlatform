package com.uavplatform.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.safety.domain.SafetyAreaEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SafetyAreaMapper extends BaseMapper<SafetyAreaEntity> {
}
