package com.example.sarisaristore.util

import com.example.sarisaristore.data.repository.DateRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object DateTimeUtils {
    @Volatile
    private var formatterBundle: FormatterBundle? = null

    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    fun formatTimestamp(timestamp: Long): String =
        formatters().dateTimeFormatter.format(Instant.ofEpochMilli(timestamp))

    fun formatDate(timestamp: Long): String =
        formatters().dateFormatter.format(Instant.ofEpochMilli(timestamp))

    fun formatTime(timestamp: Long): String =
        formatters().timeFormatter.format(Instant.ofEpochMilli(timestamp))

    fun todayRange(now: Instant = Instant.now()): DateRange {
        val zonedNow = now.atZone(zoneId)
        val start = zonedNow.toLocalDate().atStartOfDay(zoneId)
        return DateRange(
            startInclusive = start.toInstant().toEpochMilli(),
            endExclusive = start.plusDays(1).toInstant().toEpochMilli(),
        )
    }

    fun lastDaysRange(dayCount: Long, now: Instant = Instant.now()): DateRange {
        require(dayCount > 0) { "Day count must be greater than zero." }

        val zonedNow = now.atZone(zoneId)
        val startOfToday = zonedNow.toLocalDate().atStartOfDay(zoneId)
        return DateRange(
            startInclusive = startOfToday.minusDays(dayCount - 1).toInstant().toEpochMilli(),
            endExclusive = startOfToday.plusDays(1).toInstant().toEpochMilli(),
        )
    }

    fun weekRange(now: Instant = Instant.now()): DateRange {
        val zonedNow = now.atZone(zoneId)
        val startOfWeek = zonedNow
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay(zoneId)
        return DateRange(
            startInclusive = startOfWeek.toInstant().toEpochMilli(),
            endExclusive = startOfWeek.plusWeeks(1).toInstant().toEpochMilli(),
        )
    }

    fun monthRange(now: Instant = Instant.now()): DateRange {
        val zonedNow = now.atZone(zoneId)
        val startOfMonth = zonedNow
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(zoneId)
        return DateRange(
            startInclusive = startOfMonth.toInstant().toEpochMilli(),
            endExclusive = startOfMonth.plusMonths(1).toInstant().toEpochMilli(),
        )
    }

    fun lastHoursRange(hours: Long, now: Instant = Instant.now()): DateRange {
        require(hours > 0) { "Hours must be greater than zero." }
        return DateRange(
            startInclusive = now.minus(java.time.Duration.ofHours(hours)).toEpochMilli(),
            endExclusive = now.toEpochMilli(),
        )
    }

    private fun formatters(): FormatterBundle {
        val locale = Locale.getDefault()
        val zoneId = zoneId
        val cachedBundle = formatterBundle
        if (cachedBundle != null && cachedBundle.locale == locale && cachedBundle.zoneId == zoneId) {
            return cachedBundle
        }

        return FormatterBundle(
            locale = locale,
            zoneId = zoneId,
            dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zoneId),
            dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(zoneId),
            timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zoneId),
        ).also { formatterBundle = it }
    }

    private data class FormatterBundle(
        val locale: Locale,
        val zoneId: ZoneId,
        val dateTimeFormatter: DateTimeFormatter,
        val dateFormatter: DateTimeFormatter,
        val timeFormatter: DateTimeFormatter,
    )
}
