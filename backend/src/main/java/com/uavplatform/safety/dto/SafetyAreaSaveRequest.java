package com.uavplatform.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SafetyAreaSaveRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    /** 3=多边形 4=圆形 */
    @NotNull
    private Integer zoneType;

    @NotNull
    private List<SafetyCoordinateDTO> coordinates;

    private BigDecimal radiusM;
    private String color;
    private Integer lineWidth;
    private Boolean clampToGround;
}
