package com.uavplatform.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BindCodeVO {

    private String code;
    private String workspaceCode;
    private Long workspaceId;
    private String expireAt;
    private String status;
}
