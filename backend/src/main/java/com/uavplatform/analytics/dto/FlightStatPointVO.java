package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FlightStatPointVO {

    private String date;
    private int sortieCount;
    private BigDecimal durationSec;
    private BigDecimal distanceM;
}
