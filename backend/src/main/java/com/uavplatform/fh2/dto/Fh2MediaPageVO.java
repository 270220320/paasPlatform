package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Fh2MediaPageVO {

    private List<Fh2MediaFileVO> list;
    private long total;
    private long page;
    private long pageSize;
}
