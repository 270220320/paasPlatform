package com.uavplatform.mission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionReconstructVO {

    /** 是否可发起（有司空飞行任务即可；发起时再校验资源） */
    @JsonProperty("available")
    private boolean available;
    private Long jobId;
    /** pending / running / success / failed */
    private String status;
    private Long fh2ModelId;
    private String previewUrl;
    private String modelUrl;
    private String errorMessage;
}
