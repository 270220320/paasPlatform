package com.uavplatform.workarea.dto;

import com.uavplatform.safety.dto.SafetyCoordinateDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class WorkAreaVO {

    private Long id;
    private String name;
    private Integer zoneType;
    private List<SafetyCoordinateDTO> coordinates;
    private BigDecimal radiusM;
    private String color;
    private Integer lineWidth;
    private Boolean clampToGround;
    private BigDecimal minAltitude;
    private BigDecimal maxAltitude;
    private String description;
    private String status;
    private String updatedAt;
}
