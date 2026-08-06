package com.uavplatform.wayline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.wayline.domain.WaylineContentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WaylineContentMapper extends BaseMapper<WaylineContentEntity> {
}
