package com.uavplatform.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadCredentialRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String mediaType;

    @NotNull
    private Long sizeBytes;
}
