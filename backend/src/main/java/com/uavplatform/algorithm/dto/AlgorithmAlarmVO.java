package com.uavplatform.algorithm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlgorithmAlarmVO {

    private Long id;
    private Long algorithmId;
    private String algorithmName;
    private String targetLabel;
    private String source;
    private String raisedAt;
    private String payload;
}
