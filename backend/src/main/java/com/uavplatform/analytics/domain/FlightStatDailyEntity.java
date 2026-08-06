package com.uavplatform.analytics.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_flight_stat_daily")
public class FlightStatDailyEntity {

    private Long workspaceId;
    private LocalDate statDate;
    private Integer sortieCount;
    private BigDecimal durationSec;
    private BigDecimal distanceM;
    private LocalDateTime updatedAt;
}
