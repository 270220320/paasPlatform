package com.uavplatform.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceIssueVO {

    private Long id;
    private Long deviceId;
    private String description;
    private String trackingNo;
    private String status;
    private String createdAt;
}
