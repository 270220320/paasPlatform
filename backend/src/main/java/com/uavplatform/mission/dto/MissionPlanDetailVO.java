package com.uavplatform.mission.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MissionPlanDetailVO {

    private MissionPlanVO plan;
    private List<MissionExecutionVO> executions;
    private int totalCount;
    private int pendingCount;
    private int runningCount;
    private int completedCount;
    private int failedCount;
    private int cancelledCount;
}
