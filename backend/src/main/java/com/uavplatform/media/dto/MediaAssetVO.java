package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MediaAssetVO {

    private Long id;
    private String name;
    private String mediaType;
    private String payload;
    private Long sizeBytes;
    private String storageKey;
    private String source;
    private Long folderId;
    private String recycleStatus;
    private List<String> tags;
    private String createdAt;
}
