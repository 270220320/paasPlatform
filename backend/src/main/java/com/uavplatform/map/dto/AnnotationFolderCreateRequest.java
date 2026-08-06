package com.uavplatform.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnotationFolderCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String name;
}
