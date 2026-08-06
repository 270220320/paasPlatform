package com.uavplatform.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceVO {

    private Long id;
    private String sn;
    private String gatewaySn;
    private String name;
    private String deviceType;
    private String model;
    private String firmwareVersion;
    private String firmwareUpgradeStatus;
    private String flightSafetyDbVersion;
    private String workStatus;
    private String joinedAt;
    private String onlineAt;
    private String onlineState;
}
