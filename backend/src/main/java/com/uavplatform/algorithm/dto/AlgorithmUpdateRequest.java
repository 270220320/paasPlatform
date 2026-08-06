package com.uavplatform.algorithm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlgorithmUpdateRequest {

    @NotBlank
    private String status;
    private String config;
}
