package com.uavplatform.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.media.domain.MediaAssetEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAssetEntity> {
}
