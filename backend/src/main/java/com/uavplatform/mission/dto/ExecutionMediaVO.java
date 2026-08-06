package com.uavplatform.mission.dto;



import lombok.Builder;

import lombok.Data;



@Data

@Builder

public class ExecutionMediaVO {



    private Long id;

    private String name;

    /** image / video / other */

    private String mediaType;

    private String previewUrl;

}


