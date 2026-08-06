package com.uavplatform.iam.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应。
 */
@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private long expiresIn;
    private UserVO user;
}
