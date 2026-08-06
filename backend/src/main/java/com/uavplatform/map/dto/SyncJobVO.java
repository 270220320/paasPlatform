package com.uavplatform.map.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SyncJobVO {

    private Long syncJobId;
    private String status;
    private String message;
    private int successCount;
    private int failedCount;
    private int totalCount;
}
