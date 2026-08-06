package com.uavplatform.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MediaConfirmRequest {

    private Long folderId;

    @NotBlank
    private String name;

    @NotBlank
    private String mediaType;

    private String payload;

    @NotNull
    private Long sizeBytes;

    @NotBlank
    private String storageKey;

    private String source;
    private Long missionExecutionId;
    private List<String> tags;
}
