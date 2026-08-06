package com.uavplatform.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceBindRequest {

    @NotBlank(message = "workspaceCode 不能为空")
    private String workspaceCode;

    @NotBlank(message = "bindCode 不能为空")
    private String bindCode;

    @NotBlank(message = "sn 不能为空")
    private String sn;

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "deviceType 不能为空")
    private String deviceType;

    @NotBlank(message = "model 不能为空")
    private String model;

    private String firmwareVersion;
}
