package com.uavplatform.iam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RoleUpdateRequest {

    private String name;
    private String description;

    @NotNull(message = "permissionIds 不能为空")
    private List<Long> permissionIds;
}
