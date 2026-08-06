package com.uavplatform.map.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.map.dto.MapTabsMetaResponse;
import com.uavplatform.map.service.AnnotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class MapController {

    private final AnnotationService annotationService;

    @GetMapping("/tabs/meta")
    public ApiResult<MapTabsMetaResponse> tabsMeta() {
        return ApiResult.ok(annotationService.tabsMeta());
    }
}
