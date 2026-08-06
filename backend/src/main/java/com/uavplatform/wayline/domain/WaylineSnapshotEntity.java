package com.uavplatform.wayline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_wayline_snapshot")
public class WaylineSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceWaylineId;
    private String name;
    private String aircraftModel;
    private String waylineType;
    private String storageKey;
    private String structuredParams;
    private String checksum;
    private LocalDateTime createdAt;
}
