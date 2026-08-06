package com.uavplatform.iam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionVO {

    private Long id;
    private String code;
    private String module;
    private String action;
    private String name;
}
