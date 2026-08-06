package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaFolderVO {

    private Long id;
    private String name;
    private String category;
    private Long parentId;
    private String createdAt;
}
