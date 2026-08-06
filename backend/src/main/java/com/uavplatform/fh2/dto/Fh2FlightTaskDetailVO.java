package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对齐 FlightHub SDK {@code TaskDetail}。
 */
@Data
@Builder
public class Fh2FlightTaskDetailVO {

    private String uuid;
    private String name;
    private Integer taskType;
    /**
     * 0待开始 1启动失败 2执行中 3暂停 4终止 5成功 6挂起 7超时 8部分执行 9准备中 10起飞排队中
     */
    private Integer status;
    private String sn;
    private String landingDockSn;
    private String waylineUuid;
    private String beginAt;
    private String endAt;
    private String runAt;
    private String completedAt;
}
