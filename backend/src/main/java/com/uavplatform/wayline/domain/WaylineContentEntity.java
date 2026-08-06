package com.uavplatform.wayline.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_wayline_content")
public class WaylineContentEntity {

    @TableId
    private Long waylineId;
    private String storageKey;
    private String structuredParams;
    private String checksum;
    private LocalDateTime updatedAt;
}
