package com.uavplatform.map.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapTabMetaVO {

    private String key;
    private boolean enabled;
    private String reason;
    private String route;
}
