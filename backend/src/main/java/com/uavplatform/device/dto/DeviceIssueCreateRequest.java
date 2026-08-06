package com.uavplatform.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceIssueCreateRequest {

    @NotNull
    private Long deviceId;

    @NotBlank
    @Size(max = 1000)
    private String description;
}
