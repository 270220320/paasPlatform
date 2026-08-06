package com.uavplatform.iam.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemberUpdateRequest {

    @Size(max = 64, message = "人员名称长度不能超过64")
    private String displayName;

    private Long roleId;

    /** active / disabled */
    private String status;
}
