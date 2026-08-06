package com.uavplatform.map.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_sync_job_item")
public class SyncJobItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long syncJobId;
    private Long annotationId;
    private Long deviceId;
    private String result;
    private String errorMessage;
}
