package com.uavplatform.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_device")
public class DeviceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String sn;
    private String gatewaySn;
    private String name;
    private String deviceType;
    private String model;
    private String fh2DeviceModelKey;
    private String firmwareVersion;
    private String firmwareUpgradeStatus;
    private String flightSafetyDbVersion;
    private String workStatus;
    private String source;
    private LocalDateTime joinedAt;
    private LocalDateTime onlineAt;
    private LocalDateTime fh2SyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
