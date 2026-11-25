package com.dicren.autospot.ui.format

import com.dicren.autospot.data.ParkingLocationEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The history arranged by local calendar day, which is what the history sheet's calendar face
 * draws: one dot per day that holds a spot, and that day's spots underneath.
 *
 * A day without spots is absent from [spotsByDay] instead of mapping to an empty list, so the grid
 * only asks for the days it has to mark. The grouping uses the device time zone on purpose: a spot
 * saved at 23:30 belongs to the day the user remembers, not to the UTC day.
 */
class ParkingCalendar(
    locations: List<ParkingLocationEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Spots by local day, newest first inside each day, matching the history order. */
    val spotsByDay: Map<LocalDate, List<ParkingLocationEntity>> =
        locations
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .mapValues { (_, spots) -> spots.sortedWith(NEWEST_FIRST) }

    /** The day of the newest spot, which is where the calendar opens. Null with no spots. */
    val newestDay: LocalDate? = spotsByDay.keys.maxOrNull()

    /** The day of the oldest spot: the calendar does not go past it. */
    val oldestDay: LocalDate? = spotsByDay.keys.minOrNull()

    /** The days of [month] that hold at least one spot. */
    fun daysIn(month: YearMonth): Set<LocalDate> = spotsByDay.keys.filter { YearMonth.from(it) == month }.toSet()

    private companion object {
        /** Same order as the history list, so a day keeps its newest spot on top. */
        val NEWEST_FIRST: Comparator<ParkingLocationEntity> =
            compareByDescending<ParkingLocationEntity> { it.timestamp }.thenByDescending { it.id }
    }
}

/**
 * The weeks of a month on the locale's week, as rows of exactly seven cells and `null` where the
 * month does not reach. [weekdayLabels] renders the header of the same [firstDayOfWeek], so the
 * columns of the two always line up.
 */
fun monthWeeks(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
): List<List<LocalDate?>> {
    val leading = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDayOfWeek.value, 7)
    val cells = ArrayList<LocalDate?>(6 * 7)
    repeat(leading) { cells += null }
    for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
    // Only the last week is padded: a month that ends exactly on the week's last day gets no
    // empty row after it.
    while (cells.size % 7 != 0) cells += null
    return cells.chunked(7)
}

/** Abbreviated weekday names in the order the grid draws them. */
fun weekdayLabels(
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
): List<String> =
    (0..6).map { offset ->
        // DayOfWeek values run Monday = 1 … Sunday = 7, so the offset is taken modulo 7.
        val day = DayOfWeek.of((firstDayOfWeek.value - 1 + offset) % 7 + 1)
        day.getDisplayName(TextStyle.NARROW, locale)
    }

/** The locale's first day of the week, for [monthWeeks] and [weekdayLabels]. */
fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek
