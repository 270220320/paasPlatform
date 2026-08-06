package com.uavplatform.map.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.map.domain.SyncJobEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncJobMapper extends BaseMapper<SyncJobEntity> {
}
