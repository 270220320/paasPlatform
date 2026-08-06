package com.uavplatform.wayline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.wayline.domain.WaylineEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WaylineMapper extends BaseMapper<WaylineEntity> {
}
