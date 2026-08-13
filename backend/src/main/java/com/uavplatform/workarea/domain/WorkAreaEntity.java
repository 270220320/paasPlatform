package com.uavplatform.workarea.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_work_area")
public class WorkAreaEntity {

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
    /** 最低飞行高度(米) */
    private BigDecimal minAltitude;
    /** 最高飞行高度(米) */
    private BigDecimal maxAltitude;
    private String description;
    /** active/inactive */
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
