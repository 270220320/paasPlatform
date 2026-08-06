package com.uavplatform.algorithm.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_algorithm_alarm")
public class AlgorithmAlarmEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long algorithmId;
    private String targetLabel;
    private String source;
    private String payload;
    private LocalDateTime raisedAt;
    private LocalDateTime createdAt;
}
