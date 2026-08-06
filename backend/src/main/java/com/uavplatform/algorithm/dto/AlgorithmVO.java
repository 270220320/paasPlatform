package com.uavplatform.algorithm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlgorithmVO {

    private Long id;
    private String name;
    private String algoType;
    private String status;
    private String updatedAt;
}
