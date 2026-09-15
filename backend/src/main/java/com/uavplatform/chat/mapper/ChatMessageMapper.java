package com.uavplatform.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.chat.domain.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
