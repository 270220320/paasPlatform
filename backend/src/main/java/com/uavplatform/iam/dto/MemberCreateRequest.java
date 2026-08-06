package com.uavplatform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemberCreateRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 128, message = "账号长度不能超过128")
    private String account;

    @NotBlank(message = "人员名称不能为空")
    @Size(max = 64, message = "人员名称长度不能超过64")
    private String displayName;

    @NotNull(message = "角色不能为空")
    private Long roleId;

    @NotBlank(message = "初始密码不能为空")
    private String password;
}
