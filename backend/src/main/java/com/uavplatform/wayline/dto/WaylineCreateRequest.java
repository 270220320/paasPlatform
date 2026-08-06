package com.uavplatform.wayline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WaylineCreateRequest {

    @NotBlank
    @Size(max = 60)
    private String name;

    @NotBlank
    private String aircraftModel;

    @NotBlank
    private String waylineType;

    private Long folderId;
    private String structuredParams;
    private String status;
}
