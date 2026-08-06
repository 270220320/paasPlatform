package com.uavplatform.map.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.map.dto.AnnotationCreateRequest;
import com.uavplatform.map.dto.AnnotationFolderCreateRequest;
import com.uavplatform.map.dto.AnnotationFolderVO;
import com.uavplatform.map.dto.AnnotationSyncRequest;
import com.uavplatform.map.dto.AnnotationUpdateRequest;
import com.uavplatform.map.dto.AnnotationVO;
import com.uavplatform.map.dto.SyncJobVO;
import com.uavplatform.map.service.AnnotationService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @GetMapping("/annotations")
    public ApiResult<List<AnnotationVO>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) Boolean visible) {
        return ApiResult.ok(annotationService.list(folderId, purpose, visible));
    }

    @PostMapping("/annotations")
    public ApiResult<AnnotationVO> create(@Valid @RequestBody AnnotationCreateRequest request) {
        return ApiResult.ok(annotationService.create(request));
    }

    @PutMapping("/annotations/{id}")
    public ApiResult<AnnotationVO> update(@PathVariable Long id, @RequestBody AnnotationUpdateRequest request) {
        return ApiResult.ok(annotationService.update(id, request));
    }

    @DeleteMapping("/annotations/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        annotationService.delete(id);
        return ApiResult.ok(null);
    }

    @GetMapping("/annotation-folders")
    public ApiResult<List<AnnotationFolderVO>> listFolders() {
        return ApiResult.ok(annotationService.listFolders());
    }

    @PostMapping("/annotation-folders")
    public ApiResult<IdVO> createFolder(@Valid @RequestBody AnnotationFolderCreateRequest request) {
        return ApiResult.ok(annotationService.createFolder(request));
    }

    @PostMapping("/annotations/sync")
    public ApiResult<SyncJobVO> sync(@RequestBody(required = false) AnnotationSyncRequest request) {
        return ApiResult.ok(annotationService.sync(request));
    }

    @GetMapping("/annotations/sync/{jobId}")
    public ApiResult<SyncJobVO> syncJob(@PathVariable Long jobId) {
        return ApiResult.ok(annotationService.getSyncJob(jobId));
    }
}
