package com.uavplatform.media.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.media.dto.ReconstructJobCreateRequest;
import com.uavplatform.media.dto.ReconstructJobVO;
import com.uavplatform.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconstruct-jobs")
@RequiredArgsConstructor
public class ReconstructJobController {

    private final MediaService mediaService;

    @PostMapping
    public ApiResult<ReconstructJobVO> create(@Valid @RequestBody ReconstructJobCreateRequest request) {
        return ApiResult.ok(mediaService.createReconstructJob(request));
    }

    @GetMapping
    public ApiResult<PageResult<ReconstructJobVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(mediaService.pageReconstructJobs(page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResult<ReconstructJobVO> detail(@PathVariable Long id) {
        return ApiResult.ok(mediaService.reconstructJobDetail(id));
    }
}
