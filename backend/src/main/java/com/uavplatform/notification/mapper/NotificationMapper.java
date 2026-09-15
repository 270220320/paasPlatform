package com.uavplatform.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.notification.domain.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
}
