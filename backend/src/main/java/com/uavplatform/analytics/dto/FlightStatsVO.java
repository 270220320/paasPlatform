package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FlightStatsVO {

    private int sortieCount;
    private BigDecimal durationSec;
    private BigDecimal distanceM;
    private List<FlightStatPointVO> series;
}
