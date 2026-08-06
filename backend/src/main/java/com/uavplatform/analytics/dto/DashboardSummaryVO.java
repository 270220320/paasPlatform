package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryVO {

    private QuotaVO quota;
    private DockStatusVO dockStatus;
    private int aircraftCount;
    private int relayCount;
    private int memberCount;
    private int waylineCount;
    private int algorithmCount;
    private int mediaCount;
    private int modelCount;
    private int reportCount;
    private int aiAlarmCount;
    private AutomationVO automation;
    private String computedAt;
}
