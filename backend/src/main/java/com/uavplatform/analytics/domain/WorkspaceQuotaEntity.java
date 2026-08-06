package com.uavplatform.analytics.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_workspace_quota")
public class WorkspaceQuotaEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long storageTotalBytes;
    private Long storageUsedBytes;
    private Integer mappingQuota;
    private Integer mappingUsed;
    private Integer livestreamMinutesTotal;
    private Integer livestreamMinutesUsed;
    private LocalDateTime updatedAt;
}
