package com.example.sarisaristore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DateTimeUtilsTest {
    @Test
    fun `todayRange start is before end`() {
        val range = DateTimeUtils.todayRange(Instant.parse("2026-04-06T10:15:30Z"))

        assertTrue(range.startInclusive!! < range.endExclusive!!)
    }

    @Test
    fun `monthRange start is before end`() {
        val range = DateTimeUtils.monthRange(Instant.parse("2026-04-06T10:15:30Z"))

        assertTrue(range.startInclusive!! < range.endExclusive!!)
    }

    @Test
    fun `lastDaysRange one day matches todayRange`() {
        val now = Instant.parse("2026-04-06T10:15:30Z")

        val todayRange = DateTimeUtils.todayRange(now)
        val lastDayRange = DateTimeUtils.lastDaysRange(dayCount = 1, now = now)

        assertEquals(todayRange, lastDayRange)
    }

    @Test
    fun `lastDaysRange seven days shares today end and starts earlier`() {
        val now = Instant.parse("2026-04-06T10:15:30Z")

        val todayRange = DateTimeUtils.todayRange(now)
        val lastSevenDaysRange = DateTimeUtils.lastDaysRange(dayCount = 7, now = now)

        assertEquals(todayRange.endExclusive, lastSevenDaysRange.endExclusive)
        assertTrue(lastSevenDaysRange.startInclusive!! < todayRange.startInclusive!!)
    }
}
