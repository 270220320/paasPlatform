package com.uavplatform.safety.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.safety.dto.SafetyAreaSaveRequest;
import com.uavplatform.safety.dto.SafetyAreaVO;
import com.uavplatform.safety.dto.SafetyDeviceSyncVO;
import com.uavplatform.safety.dto.SafetySyncRequest;
import com.uavplatform.safety.service.SafetyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @GetMapping("/areas")
    public ApiResult<List<SafetyAreaVO>> listAreas() {
        return ApiResult.ok(safetyService.listAreas());
    }

    @PostMapping("/areas")
    public ApiResult<SafetyAreaVO> create(@Valid @RequestBody SafetyAreaSaveRequest request) {
        return ApiResult.ok(safetyService.create(request));
    }

    @PutMapping("/areas/{id}")
    public ApiResult<SafetyAreaVO> update(@PathVariable Long id, @Valid @RequestBody SafetyAreaSaveRequest request) {
        return ApiResult.ok(safetyService.update(id, request));
    }

    @DeleteMapping("/areas/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        safetyService.delete(id);
        return ApiResult.ok(null);
    }

    @PostMapping("/sync")
    public ApiResult<List<SafetyDeviceSyncVO>> sync(@Valid @RequestBody SafetySyncRequest request) {
        return ApiResult.ok(safetyService.syncToDevices(request));
    }

    @GetMapping("/sync-status")
    public ApiResult<List<SafetyDeviceSyncVO>> syncStatus(@RequestParam(required = false) List<String> deviceSns) {
        return ApiResult.ok(safetyService.listSyncStatus(deviceSns));
    }
}
