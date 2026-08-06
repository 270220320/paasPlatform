package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UploadCredentialVO {

    private String uploadUrl;
    private String storageKey;
    private Map<String, String> headers;
    private String expireAt;
}
