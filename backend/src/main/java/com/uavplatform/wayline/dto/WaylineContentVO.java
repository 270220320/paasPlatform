package com.uavplatform.wayline.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaylineContentVO {

    private String storageKey;
    private String structuredParams;
    private String checksum;
}
