package com.uavplatform.device.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_device_status")
public class DeviceStatusEntity {

    @TableId
    private Long deviceId;
    private String onlineState;
    private String workSubStatus;
    private String telemetrySnapshot;
    private LocalDateTime updatedAt;
}
