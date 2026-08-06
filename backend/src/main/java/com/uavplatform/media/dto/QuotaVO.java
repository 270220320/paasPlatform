package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaVO {

    private Long storageTotalBytes;
    private Long storageUsedBytes;
    private Integer mappingQuota;
    private Integer mappingUsed;
    private Integer livestreamMinutesTotal;
    private Integer livestreamMinutesUsed;
}
