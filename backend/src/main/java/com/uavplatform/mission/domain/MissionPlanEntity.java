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
    /** 蛙跳任务的降落机场；普通任务为空。 */
    private Long landingDeviceId;
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
