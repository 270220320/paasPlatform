package com.uavplatform.analytics.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_dashboard_snapshot")
public class DashboardSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private LocalDateTime computedAt;
    private Integer aircraftCount;
    private Integer dockIdle;
    private Integer dockWorking;
    private Integer dockDebugging;
    private Integer dockOffline;
    private Integer memberCount;
    private Integer waylineCount;
    private Integer algorithmCount;
    private Integer mediaCount;
    private Integer modelCount;
    private Integer reportCount;
    private Integer aiAlarmCount;
    private Integer workflowCount;
    private Integer workflowCycles;
    private BigDecimal workflowSuccessRate;
}
