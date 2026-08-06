package com.uavplatform.map.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnnotationFolderVO {

    private Long id;
    private String name;
    private Boolean locked;
    private String createdAt;
}
