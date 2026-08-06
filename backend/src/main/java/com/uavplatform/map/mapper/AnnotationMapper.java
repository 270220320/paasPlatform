package com.uavplatform.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.map.domain.AnnotationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnotationMapper extends BaseMapper<AnnotationEntity> {
}
