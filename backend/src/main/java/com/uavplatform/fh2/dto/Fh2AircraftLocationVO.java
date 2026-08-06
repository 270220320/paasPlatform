package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Fh2AircraftLocationVO {

    private String sn;
    private String name;
    private String gatewaySn;
    private String model;
    /** 模式码名称，如 ROUTE_FLIGHT */
    private String modeCode;
    private Double latitude;
    private Double longitude;
    private Double height;
    private Double elevation;
    /** 机头朝向角（度，北为 0，顺时针），来自 AircraftAttitude.attitudeHead */
    private Double heading;
    private Long updatedAt;
}
