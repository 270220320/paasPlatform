package com.uavplatform.wayline.dto;

import lombok.Data;

@Data
public class WaylineUpdateRequest {

    private String name;
    private String structuredParams;
    private String status;
    private Long folderId;
}
