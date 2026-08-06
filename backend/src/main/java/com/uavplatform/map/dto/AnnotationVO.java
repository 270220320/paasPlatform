package com.uavplatform.map.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnnotationVO {

    private Long id;
    private String name;
    private String geomType;
    private String purpose;
    private String geometry;
    private String style;
    private Boolean visible;
    private String syncStatus;
    private Long folderId;
    private String updatedAt;
}
