package com.uavplatform.device.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.device.dto.DeviceIssueCreateRequest;
import com.uavplatform.device.dto.DeviceIssueVO;
import com.uavplatform.device.service.DeviceIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/device-issues")
@RequiredArgsConstructor
public class DeviceIssueController {

    private final DeviceIssueService deviceIssueService;

    @PostMapping
    public ApiResult<DeviceIssueVO> create(@Valid @RequestBody DeviceIssueCreateRequest request) {
        return ApiResult.ok(deviceIssueService.create(request));
    }

    @GetMapping
    public ApiResult<List<DeviceIssueVO>> list(@RequestParam(required = false) Long deviceId) {
        return ApiResult.ok(deviceIssueService.list(deviceId));
    }
}
