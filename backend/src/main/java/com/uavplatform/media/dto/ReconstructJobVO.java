package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReconstructJobVO {

    private Long jobId;
    private String status;
    private Long modelId;
    private Long fh2ModelId;
    private Long missionExecutionId;
    private String previewUrl;
    private String modelUrl;
    private String errorMessage;
    private String createdAt;
    private String finishedAt;
}
