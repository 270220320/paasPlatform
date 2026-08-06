package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelAssetVO {

    private Long id;
    private String name;
    private String modelType;
    private Long sizeBytes;
    private String recycleStatus;
    private String createdAt;
}
