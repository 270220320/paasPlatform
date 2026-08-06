package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Fh2WaylineItemVO {

    private String waylineUuid;
    private String waylineName;
    private String droneType;
    private Long size;
    private Long updatedAt;
    private boolean aiWayline;
}
