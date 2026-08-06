package com.uavplatform.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_bind_code")
public class BindCodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String code;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime refreshedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
