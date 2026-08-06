package com.uavplatform.algorithm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlgorithmCapabilitiesVO {

    private boolean multimodalAvailable;
    private int enabledDetectionCount;
}
