package com.uavplatform.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_safety_device_sync")
public class SafetyDeviceSyncEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String deviceSn;
    private String nickname;
    private String syncStatus;
    private String syncMsg;
    private String zoneHash;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
