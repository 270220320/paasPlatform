package com.uavplatform.wayline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.wayline.domain.WaylineSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WaylineSnapshotMapper extends BaseMapper<WaylineSnapshotEntity> {
}
