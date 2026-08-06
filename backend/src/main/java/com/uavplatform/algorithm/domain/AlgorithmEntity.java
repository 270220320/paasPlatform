package com.uavplatform.algorithm.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_algorithm")
public class AlgorithmEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private String name;
    private String algoType;
    private String status;
    private String config;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
