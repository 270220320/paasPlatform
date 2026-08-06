package com.uavplatform.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MediaFolderCreateRequest {

    @NotBlank
    private String name;
    private String category;
    private Long parentId;
}
