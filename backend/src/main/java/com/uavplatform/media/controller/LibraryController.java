package com.uavplatform.media.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.media.dto.DownloadUrlVO;
import com.uavplatform.media.dto.LibraryFeaturesVO;
import com.uavplatform.media.dto.MediaAssetVO;
import com.uavplatform.media.dto.MediaConfirmRequest;
import com.uavplatform.media.dto.MediaFolderCreateRequest;
import com.uavplatform.media.dto.MediaFolderVO;
import com.uavplatform.media.dto.ModelAssetVO;
import com.uavplatform.media.dto.QuotaVO;
import com.uavplatform.media.dto.TrashItemVO;
import com.uavplatform.media.dto.UploadCredentialRequest;
import com.uavplatform.media.dto.UploadCredentialVO;
import com.uavplatform.media.service.MediaService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
public class LibraryController {

    private final MediaService mediaService;

    @PostMapping("/upload-credential")
    public ApiResult<UploadCredentialVO> uploadCredential(@Valid @RequestBody UploadCredentialRequest request) {
        return ApiResult.ok(mediaService.uploadCredential(request));
    }

    @PostMapping("/media")
    public ApiResult<MediaAssetVO> confirmMedia(@Valid @RequestBody MediaConfirmRequest request) {
        return ApiResult.ok(mediaService.confirmMedia(request));
    }

    @GetMapping("/media")
    public ApiResult<PageResult<MediaAssetVO>> pageMedia(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(mediaService.pageMedia(folderId, mediaType, keyword, page, pageSize));
    }

    @GetMapping("/media/{id}/download-url")
    public ApiResult<DownloadUrlVO> downloadUrl(@PathVariable Long id) {
        return ApiResult.ok(mediaService.downloadUrl(id));
    }

    @GetMapping("/folders")
    public ApiResult<List<MediaFolderVO>> listFolders(@RequestParam(required = false) String category) {
        return ApiResult.ok(mediaService.listFolders(category));
    }

    @PostMapping("/folders")
    public ApiResult<IdVO> createFolder(@Valid @RequestBody MediaFolderCreateRequest request) {
        return ApiResult.ok(mediaService.createFolder(request));
    }

    @GetMapping("/quota")
    public ApiResult<QuotaVO> quota() {
        return ApiResult.ok(mediaService.quota());
    }

    @GetMapping("/models")
    public ApiResult<PageResult<ModelAssetVO>> pageModels(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(mediaService.pageModels(page, pageSize));
    }

    @PostMapping("/media/{id}/trash")
    public ApiResult<Void> trash(@PathVariable Long id) {
        mediaService.trashMedia(id);
        return ApiResult.ok(null);
    }

    @PostMapping("/media/{id}/restore")
    public ApiResult<Void> restore(@PathVariable Long id) {
        mediaService.restoreMedia(id);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/media/{id}/purge")
    public ApiResult<Void> purge(@PathVariable Long id) {
        mediaService.purgeMedia(id);
        return ApiResult.ok(null);
    }

    @GetMapping("/trash")
    public ApiResult<List<TrashItemVO>> trashList() {
        return ApiResult.ok(mediaService.trashList());
    }

    @GetMapping("/features")
    public ApiResult<LibraryFeaturesVO> features() {
        return ApiResult.ok(mediaService.features());
    }
}
