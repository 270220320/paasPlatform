package com.uavplatform.wayline.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaylineListVO {

    private Long id;
    private String name;
    private String aircraftModel;
    private String waylineType;
    private String status;
    private String fh2WaylineUuid;
    private String source;
    private Long folderId;
    private String updatedAt;
}
