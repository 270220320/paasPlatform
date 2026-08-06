package com.uavplatform.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnotationCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    private String geomType;

    private String purpose;

    @NotBlank
    private String geometry;

    private String style;
    private Long folderId;
    private Boolean visible;
}
