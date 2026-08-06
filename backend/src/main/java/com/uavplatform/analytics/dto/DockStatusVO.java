package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DockStatusVO {

    private int idle;
    private int working;
    private int debugging;
    private int offline;
}
