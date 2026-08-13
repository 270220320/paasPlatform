package com.uavplatform.fh2.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.device.dto.DeviceVO;
import com.uavplatform.fh2.dto.Fh2AircraftLocationVO;
import com.uavplatform.fh2.dto.PaasBootstrapVO;
import com.uavplatform.fh2.service.Fh2IntegrationService;
import com.uavplatform.wayline.dto.WaylineListVO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/paas")
@RequiredArgsConstructor
public class PaasController {

    private final Fh2IntegrationService fh2IntegrationService;

    /** 前端加载 paas.js / WaylineEditor / DeviceManager 所需配置与 token */
    @GetMapping("/bootstrap")
    public ApiResult<PaasBootstrapVO> bootstrap() {
        return ApiResult.ok(fh2IntegrationService.bootstrap());
    }

    @GetMapping("/sdk/status")
    public ApiResult<Map<String, Object>> sdkStatus() {
        return ApiResult.ok(fh2IntegrationService.sdkStatus());
    }

    @PostMapping("/sdk/reload")
    public ApiResult<Map<String, Object>> reloadSdk() {
        fh2IntegrationService.reloadSdk();
        return ApiResult.ok(fh2IntegrationService.sdkStatus());
    }

    @PostMapping("/devices/sync")
    public ApiResult<Map<String, Object>> syncDevices() {
        int n = fh2IntegrationService.syncDevices();
        return ApiResult.ok(Map.of("synced", n));
    }

    @GetMapping("/devices/remote")
    public ApiResult<PageResult<DeviceVO>> remoteDevices() {
        return ApiResult.ok(fh2IntegrationService.previewRemoteDevices());
    }

    /** 在飞飞行器实时位置（首页地图标注） */
    @GetMapping("/aircraft/locations")
    public ApiResult<List<Fh2AircraftLocationVO>> flyingAircraftLocations() {
        return ApiResult.ok(fh2IntegrationService.listFlyingAircraftLocations());
    }

    @PostMapping("/waylines/sync")
    public ApiResult<Map<String, Object>> syncWaylines() {
        int n = fh2IntegrationService.syncWaylines();
        return ApiResult.ok(Map.of("synced", n));
    }

    /**
     * WaylineEditor 保存/改名后回写本地航线镜像（名称 / 文件夹）。
     * name 为空时由服务端从司空拉取最新名称。
     */
    @PostMapping("/waylines/upsert")
    public ApiResult<WaylineListVO> upsertWayline(@RequestBody UpsertWaylineReq req) {
        return ApiResult.ok(fh2IntegrationService.upsertWaylineFromEditor(
                req.getWaylineUuid(), req.getName(), req.getFolderId()));
    }

    @Data
    public static class UpsertWaylineReq {
        @NotBlank
        private String waylineUuid;
        private String name;
        private Long folderId;
    }
}
