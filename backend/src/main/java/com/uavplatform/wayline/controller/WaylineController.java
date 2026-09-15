package com.uavplatform.wayline.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.wayline.dto.WaylineCreateRequest;
import com.uavplatform.wayline.dto.WaylineDetailVO;
import com.uavplatform.wayline.dto.WaylineExportFile;
import com.uavplatform.wayline.dto.WaylineFolderCreateRequest;
import com.uavplatform.wayline.dto.WaylineFolderVO;
import com.uavplatform.wayline.dto.WaylineListVO;
import com.uavplatform.wayline.dto.WaylineUpdateRequest;
import com.uavplatform.wayline.service.WaylineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WaylineController {

    private final WaylineService waylineService;

    @GetMapping("/wayline-folders")
    public ApiResult<List<WaylineFolderVO>> listFolders() {
        return ApiResult.ok(waylineService.listFolders());
    }

    @PostMapping("/wayline-folders")
    public ApiResult<IdVO> createFolder(@Valid @RequestBody WaylineFolderCreateRequest request) {
        return ApiResult.ok(waylineService.createFolder(request));
    }

    @DeleteMapping("/wayline-folders/{id}")
    public ApiResult<Void> deleteFolder(@PathVariable Long id) {
        waylineService.deleteFolder(id);
        return ApiResult.ok(null);
    }

    @GetMapping("/waylines")
    public ApiResult<PageResult<WaylineListVO>> page(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String aircraftModel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(waylineService.page(folderId, aircraftModel, status, keyword, page, pageSize));
    }

    @GetMapping("/waylines/{id}")
    public ApiResult<WaylineDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(waylineService.detail(id));
    }

    @PostMapping("/waylines")
    public ApiResult<WaylineDetailVO> create(@Valid @RequestBody WaylineCreateRequest request) {
        return ApiResult.ok(waylineService.create(request));
    }

    @PutMapping("/waylines/{id}")
    public ApiResult<WaylineDetailVO> update(@PathVariable Long id, @RequestBody WaylineUpdateRequest request) {
        return ApiResult.ok(waylineService.update(id, request));
    }

    @DeleteMapping("/waylines/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        waylineService.delete(id);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/waylines/uuid/{waylineUuid}")
    public ApiResult<Void> deleteByUuid(@PathVariable String waylineUuid) {
        waylineService.deleteByUuid(waylineUuid);
        return ApiResult.ok(null);
    }

    @PostMapping(value = "/waylines/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<WaylineDetailVO> importWayline(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String aircraftModel) {
        return ApiResult.ok(waylineService.importFile(file, folderId, aircraftModel));
    }

    /** 导出（U-032）：KMZ 本体优先，降级 JSON；文件名 RFC 5987 编码支持中文 */
    @GetMapping("/waylines/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        WaylineExportFile file = waylineService.export(id);
        String encodedName = UriUtils.encode(file.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.data());
    }
}
