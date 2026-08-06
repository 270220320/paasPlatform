package com.uavplatform.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.map.domain.SyncJobItemEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncJobItemMapper extends BaseMapper<SyncJobItemEntity> {
}
