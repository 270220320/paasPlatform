package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对齐 FlightHub SDK {@code CreateTaskReq} / Apifox FlightTaskModule.createFlightTask。
 */
@Data
@Builder
public class Fh2CreateFlightTaskCmd {

    private String name;
    /** 起飞机场 SN */
    private String sn;
    /** 1=立即 2=条件/定时 */
    private int taskType;
    @Builder.Default
    private String timeZone = "Asia/Shanghai";
    private String waylineUuid;
    /** 1=返航 2=继续执行 */
    private int outOfControlActionInFlight;
    /** 返航高度（米） */
    private Double rthAltitude;
    /** 0=智能高度 1=设定高度 */
    private Integer rthMode;
    /** 条件任务 begin/end（秒级 Unix） */
    private Long beginAt;
    private Long endAt;
    /** 0=机场 1=遥控器；仅显式传入时下发，避免默认 0 触发 SDK 校验 */
    private Integer deviceType;
    private String landingDockSn;
    private Integer minBatteryCapacity;
    /** 0=GPS 1=RTK */
    private Integer waylinePrecisionType;
    private Boolean breakPointResume;
}
