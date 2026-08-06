package com.uavplatform.algorithm.dto;

import lombok.Data;

@Data
public class AlgorithmAlarmCreateRequest {

    private Long algorithmId;
    private String targetLabel;
    private String source;
    private String payload;
}
