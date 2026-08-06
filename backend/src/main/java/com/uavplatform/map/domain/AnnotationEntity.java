package com.uavplatform.map.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_annotation")
public class AnnotationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long folderId;
    private String name;
    private String geomType;
    private String purpose;
    private String geometry;
    private String style;
    private Integer visible;
    private String syncStatus;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
