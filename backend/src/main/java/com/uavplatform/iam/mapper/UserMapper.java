package com.uavplatform.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uavplatform.iam.domain.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
