package com.uavplatform.media.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReconstructJobCreateRequest {

    @NotEmpty
    private List<Long> mediaIds;
}
