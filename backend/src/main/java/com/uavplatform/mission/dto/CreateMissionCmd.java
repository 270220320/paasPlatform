package com.uavplatform.mission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateMissionCmd {

    @NotBlank(message = "计划名称不能为空")
    @Size(max = 128)
    private String name;

    @NotBlank(message = "任务类型不能为空")
    private String taskType;

    private Long waylineId;

    /** 本地设备 ID；与 deviceSn 二选一，司空设备列表通常传 SN */
    private Long deviceId;

    /** 机场/网关 SN（与驾驶舱设备列表一致） */
    private String deviceSn;

    @NotBlank(message = "任务策略不能为空")
    private String strategy;

    /** 兼容旧字段：单次定时可直接传完整时间 */
    private String scheduleAt;
    private String cronExpr;

    /** 单次定时：执行日期 yyyy-MM-dd */
    private String executeDate;

    /** 重复/连续：执行日期范围 */
    private String dateStart;
    private String dateEnd;

    /** 执行时段（最早开始~最晚开始），可多段 */
    private List<TimeWindow> timeWindows;

    /** 重复频率：每 N 天/周/月 */
    private Integer freqInterval;
    /** day / week / month */
    private String freqUnit;

    /** 连续执行：任务开始电量 % */
    private Integer minBatteryPercent;

    @NotBlank(message = "任务精度不能为空")
    private String accuracyMode;

    private Boolean breakpointResume;
    private Boolean smartRth;

    /** 最低航点高度；前端清空时可能为 null，服务端会默认 30 */
    private BigDecimal minFlightHeightM;

    @NotBlank(message = "失联动作不能为空")
    private String signalLostAction;

    private Boolean multimodalEnabled;

    @Data
    public static class TimeWindow {
        private String start;
        private String end;
    }
}
