package com.uavplatform.media.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_reconstruct_job")
public class ReconstructJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String status;
    private Long modelId;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
