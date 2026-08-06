package com.uavplatform.analytics.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_mission_stat_daily")
public class MissionStatDailyEntity {

    private Long workspaceId;
    private LocalDate statDate;
    private Integer plannedCount;
    private Integer manualCount;
    private Integer successCount;
    private Integer pausedCount;
    private Integer failedCount;
    private LocalDateTime updatedAt;
}
