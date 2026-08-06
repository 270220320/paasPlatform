package com.uavplatform.wayline.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaylineDetailVO {

    private Long id;
    private String name;
    private String aircraftModel;
    private String waylineType;
    private String status;
    private Long folderId;
    private String updatedAt;
    private WaylineContentVO content;
}
