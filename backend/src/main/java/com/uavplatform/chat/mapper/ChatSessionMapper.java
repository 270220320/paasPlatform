package com.uavplatform.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.chat.domain.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
