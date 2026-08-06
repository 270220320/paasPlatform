package com.uavplatform.wayline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_wayline")
public class WaylineEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long folderId;
    private String name;
    private String aircraftModel;
    private String waylineType;
    private String status;
    private String fh2WaylineUuid;
    private String source;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
