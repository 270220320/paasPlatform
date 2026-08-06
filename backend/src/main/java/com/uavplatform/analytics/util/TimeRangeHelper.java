package com.uavplatform.analytics.util;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * 看板时间范围解析。
 */
public final class TimeRangeHelper {

    private static final Set<String> RANGES = Set.of(
            "week", "month", "lastWeek", "lastMonth", "halfYear", "year", "custom",
            "7d", "30d", "90d");

    private TimeRangeHelper() {
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }

    public static DateRange resolve(String range, LocalDate startDate, LocalDate endDate) {
        String r = range == null || range.isBlank() ? "week" : range.trim();
        if (!RANGES.contains(r)) {
            throw new BizException(ErrorCodes.TIME_RANGE_INVALID, "时间范围非法");
        }
        LocalDate today = LocalDate.now();
        return switch (r) {
            case "week" -> new DateRange(today.with(DayOfWeek.MONDAY), today);
            case "month" -> new DateRange(today.withDayOfMonth(1), today);
            case "lastWeek" -> {
                LocalDate lastWeekEnd = today.with(DayOfWeek.MONDAY).minusDays(1);
                yield new DateRange(lastWeekEnd.with(DayOfWeek.MONDAY), lastWeekEnd);
            }
            case "lastMonth" -> {
                LocalDate firstOfThisMonth = today.withDayOfMonth(1);
                LocalDate lastMonthEnd = firstOfThisMonth.minusDays(1);
                yield new DateRange(lastMonthEnd.withDayOfMonth(1), lastMonthEnd);
            }
            case "halfYear" -> new DateRange(today.minusMonths(6).plusDays(1), today);
            case "year" -> new DateRange(today.with(TemporalAdjusters.firstDayOfYear()), today);
            case "7d" -> new DateRange(today.minusDays(6), today);
            case "30d" -> new DateRange(today.minusDays(29), today);
            case "90d" -> new DateRange(today.minusDays(89), today);
            case "custom" -> {
                if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
                    throw new BizException(ErrorCodes.TIME_RANGE_INVALID, "时间范围非法");
                }
                yield new DateRange(startDate, endDate);
            }
            default -> throw new BizException(ErrorCodes.TIME_RANGE_INVALID, "时间范围非法");
        };
    }
}
