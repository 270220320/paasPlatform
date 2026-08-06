package com.uavplatform.map.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnnotationSyncRequest {

    private List<Long> annotationIds;
    private List<Long> deviceIds;
}
