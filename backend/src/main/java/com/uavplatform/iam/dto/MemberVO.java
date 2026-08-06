package com.uavplatform.iam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberVO {

    private Long id;
    private String account;
    private String displayName;
    private Long roleId;
    private String roleName;
    private String joinMethod;
    private String joinedAt;
    private String status;
}
