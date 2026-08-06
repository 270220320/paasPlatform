package com.uavplatform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 128, message = "账号长度不能超过128")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
