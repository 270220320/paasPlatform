package com.uavplatform.iam.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户视图。
 */
@Data
@Builder
public class UserVO {

    private Long id;
    private String account;
    private String displayName;
    private String roleCode;
    private String roleName;
    private List<String> permissions;
}
