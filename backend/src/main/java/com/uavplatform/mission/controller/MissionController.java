package com.uavplatform.mission.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.mission.dto.CreateMissionCmd;
import com.uavplatform.mission.dto.MissionPlanDetailVO;
import com.uavplatform.mission.dto.MissionPlanVO;
import com.uavplatform.mission.service.MissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @GetMapping
    public ApiResult<PageResult<MissionPlanVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(missionService.page(status, deviceId, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResult<MissionPlanDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(missionService.detail(id));
    }

    @PostMapping
    public ApiResult<MissionPlanVO> create(@Valid @RequestBody CreateMissionCmd cmd) {
        return ApiResult.ok(missionService.create(cmd));
    }

    @PostMapping("/{id}/actions/pause")
    public ApiResult<MissionPlanVO> pause(@PathVariable Long id) {
        return ApiResult.ok(missionService.pause(id));
    }

    @PostMapping("/{id}/actions/resume")
    public ApiResult<MissionPlanVO> resume(@PathVariable Long id) {
        return ApiResult.ok(missionService.resume(id));
    }

    @PostMapping("/{id}/actions/cancel")
    public ApiResult<MissionPlanVO> cancel(@PathVariable Long id) {
        return ApiResult.ok(missionService.cancel(id));
    }

    @PostMapping("/{id}/actions/copy")
    public ApiResult<MissionPlanVO> copy(@PathVariable Long id) {
        return ApiResult.ok(missionService.copy(id));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        missionService.delete(id);
        return ApiResult.ok(null);
    }
}
