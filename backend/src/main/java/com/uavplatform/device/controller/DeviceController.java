package com.uavplatform.device.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.device.dto.BindCodeVO;
import com.uavplatform.device.dto.DeviceBindRequest;
import com.uavplatform.device.dto.DeviceVO;
import com.uavplatform.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备接口（U-060 / U-061）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/devices")
    public ApiResult<PageResult<DeviceVO>> page(
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String workStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(deviceService.page(deviceType, keyword, workStatus, page, pageSize));
    }

    @GetMapping("/bind-codes/current")
    public ApiResult<BindCodeVO> currentBindCode() {
        return ApiResult.ok(deviceService.currentBindCode());
    }

    @PostMapping("/bind-codes/refresh")
    public ApiResult<BindCodeVO> refreshBindCode() {
        return ApiResult.ok(deviceService.refreshBindCode());
    }

    /** 现场端绑定（匿名，凭绑定码）。 */
    @PostMapping("/devices/bind")
    public ApiResult<DeviceVO> bind(@Valid @RequestBody DeviceBindRequest request) {
        return ApiResult.ok(deviceService.bind(request));
    }
}
