package com.uavplatform.wayline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WaylineFolderCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String name;
    private Long parentId;
}
