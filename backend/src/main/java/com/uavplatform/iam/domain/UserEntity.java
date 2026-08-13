package com.uavplatform.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 t_user。
 */
@Data
@TableName("t_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String account;
    private String displayName;
    private String email;
    private String passwordHash;
    private String status;
    private String joinMethod;
    private LocalDateTime joinedAt;
    private LocalDateTime lastLoginAt;
    private Long roleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
