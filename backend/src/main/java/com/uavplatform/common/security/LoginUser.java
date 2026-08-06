package com.uavplatform.common.security;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 登录用户上下文。
 */
@Getter
@Setter
public class LoginUser {

    private Long userId;
    private Long workspaceId;
    private String roleCode;
    private List<String> permissions = Collections.emptyList();
    private String token;
}
