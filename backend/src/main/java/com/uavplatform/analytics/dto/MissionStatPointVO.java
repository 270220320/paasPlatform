package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MissionStatPointVO {

    private String date;
    private int plannedCount;
    private int manualCount;
    private int successCount;
    private int pausedCount;
    private int failedCount;
}
