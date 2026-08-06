package com.uavplatform.media.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_media_asset")
public class MediaAssetEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long folderId;
    private String name;
    private String mediaType;
    private String payload;
    private Long sizeBytes;
    private String storageKey;
    private String source;
    private Long missionExecutionId;
    private String recycleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
