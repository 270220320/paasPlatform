package com.uavplatform.workarea.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.workarea.dto.WorkAreaSaveRequest;
import com.uavplatform.workarea.dto.WorkAreaVO;
import com.uavplatform.workarea.service.WorkAreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work-areas")
@RequiredArgsConstructor
public class WorkAreaController {

    private final WorkAreaService workAreaService;

    @GetMapping
    public ApiResult<List<WorkAreaVO>> list() {
        return ApiResult.ok(workAreaService.listAreas());
    }

    @PostMapping
    public ApiResult<WorkAreaVO> create(@Valid @RequestBody WorkAreaSaveRequest request) {
        return ApiResult.ok(workAreaService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResult<WorkAreaVO> update(@PathVariable Long id, @Valid @RequestBody WorkAreaSaveRequest request) {
        return ApiResult.ok(workAreaService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        workAreaService.delete(id);
        return ApiResult.ok(null);
    }
}
