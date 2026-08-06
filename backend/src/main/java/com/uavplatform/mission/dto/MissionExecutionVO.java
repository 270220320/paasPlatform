package com.uavplatform.mission.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MissionExecutionVO {

    private Long id;
    /** 真实执行记录 / 由调度推算的待执行槽位 */
    private String recordType;
    private String status;
    private String plannedAt;
    private String actualStartAt;
    private String actualEndAt;
    private BigDecimal flightDurationSec;
    private BigDecimal flightDistanceM;
    private String failReason;
    /** 司空飞行任务 UUID（回放用） */
    private String fh2TaskUuid;
    /** 本条执行收集到的图片/视频 */
    private List<ExecutionMediaVO> media;
}
