package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Fh2DeviceItemVO {

    private String sn;
    private String name;
    private String deviceType;
    private String model;
    private String gatewaySn;
    private String workStatus;
    private boolean online;
}
