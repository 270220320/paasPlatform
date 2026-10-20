package com.uavplatform.mission.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MissionPlanVO {

    private Long id;
    private String name;
    private String taskType;
    private Long waylineId;
    private Long waylineSnapshotId;
    /** 关联航线是否面状（仅面状显示三维重建入口；detail 接口下发） */
    private Boolean areaWayline;
    private Long deviceId;
    private String deviceName;
    private Long landingDeviceId;
    private String landingDeviceName;
    private String waylineName;
    private String strategy;
    private String scheduleAt;
    private String cronExpr;
    private String scheduleConfig;
    private String accuracyMode;
    private Boolean breakpointResume;
    private Boolean smartRth;
    private BigDecimal minFlightHeightM;
    private String signalLostAction;
    private Boolean multimodalEnabled;
    private String status;
    private String createdAt;
    private String updatedAt;
}
