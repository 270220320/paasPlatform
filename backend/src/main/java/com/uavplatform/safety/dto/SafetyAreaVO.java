package com.uavplatform.safety.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SafetyAreaVO {

    private Long id;
    private String name;
    private Integer zoneType;
    private List<SafetyCoordinateDTO> coordinates;
    private BigDecimal radiusM;
    private String color;
    private Integer lineWidth;
    private Boolean clampToGround;
    private String syncStatus;
    private String updatedAt;
}
