package com.uavplatform.mission.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionMediaVO {

    /** 本地媒体 ID 或司空 fileId（OpenAPI 任务媒体可能为空） */
    private Long id;
    /** 司空媒体 UUID（OpenAPI） */
    private String uuid;
    private String name;
    /** image / video / other */
    private String mediaType;
    private String previewUrl;
    private String downloadUrl;
}
