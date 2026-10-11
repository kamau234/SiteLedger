package com.siteledger.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ReportPeriodTest {

    @Test
    fun `today preset covers a single day`() {
        val today = LocalDate.of(2026, 10, 11)
        val period = ReportPeriod.resolve(ReportPreset.TODAY, today)
        assertEquals(today, period.from)
        assertEquals(today, period.to)
        assertTrue(period.contains(today))
        assertFalse(period.contains(today.minusDays(1)))
    }

    @Test
    fun `this week starts on monday`() {
        // 2026-10-11 is a Sunday.
        val sunday = LocalDate.of(2026, 10, 11)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        val period = ReportPeriod.resolve(ReportPreset.THIS_WEEK, sunday)
        assertEquals(LocalDate.of(2026, 10, 5), period.from)
        assertEquals(sunday, period.to)
        assertTrue(period.contains(LocalDate.of(2026, 10, 5)))
        assertFalse(period.contains(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun `monday start week contains that same monday`() {
        val monday = LocalDate.of(2026, 10, 5)
        val period = ReportPeriod.resolve(ReportPreset.THIS_WEEK, monday)
        assertEquals(monday, period.from)
    }

    @Test
    fun `this month starts on the first`() {
        val period = ReportPeriod.resolve(ReportPreset.THIS_MONTH, LocalDate.of(2026, 10, 11))
        assertEquals(LocalDate.of(2026, 10, 1), period.from)
        assertEquals(LocalDate.of(2026, 10, 11), period.to)
    }

    @Test
    fun `last month covers the full previous calendar month`() {
        val period = ReportPeriod.resolve(ReportPreset.LAST_MONTH, LocalDate.of(2026, 10, 11))
        assertEquals(LocalDate.of(2026, 9, 1), period.from)
        assertEquals(LocalDate.of(2026, 9, 30), period.to)
    }

    @Test
    fun `last month works across a year boundary`() {
        val period = ReportPeriod.resolve(ReportPreset.LAST_MONTH, LocalDate.of(2026, 1, 15))
        assertEquals(LocalDate.of(2025, 12, 1), period.from)
        assertEquals(LocalDate.of(2025, 12, 31), period.to)
    }

    @Test
    fun `all time has open bounds and contains every date`() {
        val period = ReportPeriod.resolve(ReportPreset.ALL_TIME, LocalDate.of(2026, 10, 11))
        assertEquals(null, period.from)
        assertEquals(null, period.to)
        assertTrue(period.contains(LocalDate.of(1999, 1, 1)))
        assertTrue(period.contains(LocalDate.of(2099, 12, 31)))
    }

    @Test
    fun `contains respects inclusive bounds`() {
        val period = ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertTrue(period.contains(LocalDate.of(2026, 10, 1)))
        assertTrue(period.contains(LocalDate.of(2026, 10, 31)))
        assertFalse(period.contains(LocalDate.of(2026, 9, 30)))
        assertFalse(period.contains(LocalDate.of(2026, 11, 1)))
    }
}
