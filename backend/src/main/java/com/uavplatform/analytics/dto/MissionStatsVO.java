package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MissionStatsVO {

    private int plannedCount;
    private int manualCount;
    private int successCount;
    private int pausedCount;
    private int failedCount;
    private List<MissionStatPointVO> series;
}
