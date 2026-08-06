package com.uavplatform.safety.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_safety_area")
public class SafetyAreaEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String name;
    /** 3=多边形 4=圆形 */
    private Integer zoneType;
    private String content;
    private String color;
    private Integer lineWidth;
    private BigDecimal radiusM;
    private Integer clampToGround;
    private String syncStatus;
    /** 司空 / dji2 侧飞行安全区 UUID，同步下发用 */
    private String fh2FlightAreaId;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
