package com.uavplatform.iam.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.iam.dto.ChangePasswordRequest;
import com.uavplatform.iam.dto.LoginRequest;
import com.uavplatform.iam.dto.LoginResponse;
import com.uavplatform.iam.dto.UpdateEmailRequest;
import com.uavplatform.iam.dto.UserVO;
import com.uavplatform.iam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（U-001）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录。
     */
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        authService.logout();
        return ApiResult.ok(null);
    }

    /**
     * 当前用户。
     */
    @GetMapping("/me")
    public ApiResult<UserVO> me() {
        return ApiResult.ok(authService.currentUser());
    }

    /**
     * 修改当前用户邮箱。
     */
    @PutMapping("/email")
    public ApiResult<Void> updateEmail(@Valid @RequestBody UpdateEmailRequest request) {
        authService.updateEmail(request);
        return ApiResult.ok(null);
    }

    /**
     * 修改当前用户密码。
     */
    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResult.ok(null);
    }
}
