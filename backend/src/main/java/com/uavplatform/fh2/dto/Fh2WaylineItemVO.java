package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Fh2WaylineItemVO {

    private String waylineUuid;
    private String waylineName;
    private String droneType;
    private Long size;
    private Long updatedAt;
    private boolean aiWayline;
    /** 司空航线类型（WaylineModelType：0=航点，1=面状，2=斜面，3=几何体，4=带状，7=贴近，8=AI 巡逻） */
    private List<Integer> waylineTypes;
}
