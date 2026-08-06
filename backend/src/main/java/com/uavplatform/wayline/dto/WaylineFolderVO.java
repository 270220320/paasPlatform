package com.uavplatform.wayline.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaylineFolderVO {

    private Long id;
    private String name;
    private Long parentId;
    private String createdAt;
}
