package com.uavplatform.mission.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_mission_plan")
public class MissionPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String name;
    private String taskType;
    private Long waylineId;
    private Long waylineSnapshotId;
    private Long deviceId;
    private String strategy;
    private LocalDateTime scheduleAt;
    private String cronExpr;
    private String scheduleConfig;
    private String accuracyMode;
    private Integer breakpointResume;
    private Integer smartRth;
    private BigDecimal minFlightHeightM;
    private String signalLostAction;
    private Integer multimodalEnabled;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
