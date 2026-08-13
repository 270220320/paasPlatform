package com.uavplatform.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改邮箱请求。
 */
@Data
public class UpdateEmailRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过255")
    private String email;
}
