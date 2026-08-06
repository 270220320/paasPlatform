package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutomationVO {

    private int workflowCount;
    private int workflowCycles;
    private double workflowSuccessRate;
}
