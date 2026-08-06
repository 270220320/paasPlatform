package com.uavplatform.safety.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SafetyDeviceSyncVO {

    private String deviceSn;
    private String nickname;
    private String syncStatus;
    private String syncMsg;
    private String zoneHash;
    private String updatedAt;
}
