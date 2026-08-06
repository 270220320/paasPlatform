package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Fh2MediaFileVO {

    private Long fileId;
    private String fileName;
    private String suffix;
    private String objectKey;
    private String filePath;
    private Long sizeBytes;
    private String deviceSn;
    private String flightId;
    private String payloadType;
    private String createdAt;
    /** image / video / unknown */
    private String mediaType;
}
