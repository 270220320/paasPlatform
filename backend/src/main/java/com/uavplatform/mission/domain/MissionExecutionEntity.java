package com.uavplatform.mission.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_mission_execution")
public class MissionExecutionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private String status;
    private LocalDateTime plannedAt;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    private BigDecimal flightDurationSec;
    private BigDecimal flightDistanceM;
    private String failReason;
    private String breakpointInfo;
    /** 司空飞行任务 UUID，供 FlyDataReplay.taskId 使用 */
    private String fh2TaskUuid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
