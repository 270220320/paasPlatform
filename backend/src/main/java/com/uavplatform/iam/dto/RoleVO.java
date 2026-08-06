package com.uavplatform.iam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean isSystem;
    private Boolean locked;
    private List<Long> permissionIds;
    private List<String> permissionCodes;
}
