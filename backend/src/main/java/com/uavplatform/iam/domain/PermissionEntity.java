package com.uavplatform.iam.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点，对应 t_permission。
 */
@Data
@TableName("t_permission")
public class PermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String module;
    private String action;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
