package com.uavplatform.algorithm.controller;

import com.uavplatform.algorithm.dto.AlgorithmAlarmCreateRequest;
import com.uavplatform.algorithm.dto.AlgorithmAlarmVO;
import com.uavplatform.algorithm.dto.AlgorithmCapabilitiesVO;
import com.uavplatform.algorithm.dto.AlgorithmUpdateRequest;
import com.uavplatform.algorithm.dto.AlgorithmVO;
import com.uavplatform.algorithm.service.AlgorithmService;
import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlgorithmController {

    private final AlgorithmService algorithmService;

    @GetMapping("/algorithms")
    public ApiResult<List<AlgorithmVO>> list() {
        return ApiResult.ok(algorithmService.list());
    }

    @GetMapping("/algorithms/capabilities")
    public ApiResult<AlgorithmCapabilitiesVO> capabilities() {
        return ApiResult.ok(algorithmService.capabilities());
    }

    @PutMapping("/algorithms/{id}")
    public ApiResult<AlgorithmVO> update(@PathVariable Long id, @Valid @RequestBody AlgorithmUpdateRequest request) {
        return ApiResult.ok(algorithmService.update(id, request));
    }

    @GetMapping("/algorithm-alarms")
    public ApiResult<PageResult<AlgorithmAlarmVO>> listAlarms(
            @RequestParam(required = false) Long algorithmId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(algorithmService.listAlarms(algorithmId, start, end, page, pageSize));
    }

    @PostMapping("/algorithm-alarms")
    public ApiResult<IdVO> createAlarm(@RequestBody AlgorithmAlarmCreateRequest request) {
        return ApiResult.ok(algorithmService.createAlarm(request));
    }
}
