package com.uavplatform.analytics.controller;

import com.uavplatform.analytics.dto.DashboardSummaryVO;
import com.uavplatform.analytics.dto.FlightStatsVO;
import com.uavplatform.analytics.dto.MissionStatsVO;
import com.uavplatform.analytics.service.DashboardService;
import com.uavplatform.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResult<DashboardSummaryVO> summary(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.ok(dashboardService.summary(range, startDate, endDate));
    }

    @GetMapping("/flight-stats")
    public ApiResult<FlightStatsVO> flightStats(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.ok(dashboardService.flightStats(range, startDate, endDate));
    }

    @GetMapping("/mission-stats")
    public ApiResult<MissionStatsVO> missionStats(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.ok(dashboardService.missionStats(range, startDate, endDate));
    }
}
