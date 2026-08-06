package com.uavplatform.map.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnotationUpdateRequest {

    @Size(max = 128)
    private String name;
    private String geomType;
    private String purpose;
    private String geometry;
    private String style;
    private Long folderId;
    private Boolean visible;
}
