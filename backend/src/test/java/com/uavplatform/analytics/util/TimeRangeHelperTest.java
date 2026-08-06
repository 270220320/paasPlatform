package com.uavplatform.analytics.util;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeRangeHelperTest {

    @Test
    void resolve_invalidRange_shouldFail() {
        BizException ex = assertThrows(BizException.class,
                () -> TimeRangeHelper.resolve("invalid", null, null));
        assertEquals(ErrorCodes.TIME_RANGE_INVALID, ex.getCode());
    }

    @Test
    void resolve_customValid_shouldPass() {
        TimeRangeHelper.DateRange dr = TimeRangeHelper.resolve("custom",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertNotNull(dr);
        assertEquals(LocalDate.of(2026, 1, 1), dr.start());
    }

    @Test
    void resolve_rollingDays_shouldPass() {
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6), TimeRangeHelper.resolve("7d", null, null).start());
        assertEquals(today.minusDays(29), TimeRangeHelper.resolve("30d", null, null).start());
        assertEquals(today.minusDays(89), TimeRangeHelper.resolve("90d", null, null).start());
        assertEquals(today, TimeRangeHelper.resolve("30d", null, null).end());
    }
}
