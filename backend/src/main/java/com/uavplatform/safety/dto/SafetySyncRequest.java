package com.uavplatform.safety.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SafetySyncRequest {

    @NotEmpty
    private List<String> deviceSns;

    /** optional sn -> nickname */
    private Map<String, String> nicknames;
}
