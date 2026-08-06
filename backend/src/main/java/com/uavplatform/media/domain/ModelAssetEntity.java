package com.uavplatform.media.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_model_asset")
public class ModelAssetEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String name;
    private String modelType;
    private String storageKey;
    private Long sizeBytes;
    private String recycleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
