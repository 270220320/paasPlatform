package com.uavplatform.mission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionReconstructVO {

    /** 是否可发起（有司空飞行任务 + 已采集媒体 + 面状航线；发起时再校验资源） */
    @JsonProperty("available")
    private boolean available;
    /** 不可发起原因：no_task / no_media / not_area_wayline / active_job，前端据此展示 tooltip */
    @JsonProperty("unavailableReason")
    private String unavailableReason;
    private Long jobId;
    /** pending / running / success / failed */
    private String status;
    private Long fh2ModelId;
    private String previewUrl;
    private String modelUrl;
    private String errorMessage;
}
