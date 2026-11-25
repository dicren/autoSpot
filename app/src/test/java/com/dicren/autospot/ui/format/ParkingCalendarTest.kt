package com.dicren.autospot.ui.format

import com.dicren.autospot.data.ParkingLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ParkingCalendarTest {
    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")

    private fun spot(
        id: Long,
        at: String,
    ) = ParkingLocationEntity(
        id = id,
        vehicleId = 1,
        latitude = 0.0,
        longitude = 0.0,
        timestamp = ZonedDateTime.parse(at).toInstant().toEpochMilli(),
    )

    @Test
    fun `spots are grouped by their day in the device time zone`() {
        val calendar =
            ParkingCalendar(
                listOf(
                    // 00:30 on the 15th in Madrid, still the 14th in UTC.
                    spot(1, "2026-01-14T23:30:00Z"),
                    spot(2, "2026-01-15T21:00:00Z"),
                    spot(3, "2026-01-15T08:00:00Z"),
                ),
                madrid,
            )

        assertEquals(setOf(LocalDate.of(2026, 1, 15)), calendar.spotsByDay.keys)
        assertEquals(
            listOf(2L, 3L, 1L),
            calendar.spotsByDay.getValue(LocalDate.of(2026, 1, 15)).map { it.id },
        )
        assertEquals(LocalDate.of(2026, 1, 15), calendar.newestDay)
        assertEquals(LocalDate.of(2026, 1, 15), calendar.oldestDay)
    }

    @Test
    fun `the days of a month are the ones that hold spots`() {
        val calendar =
            ParkingCalendar(
                listOf(
                    spot(1, "2026-01-14T12:00:00Z"),
                    spot(2, "2026-01-14T18:00:00Z"),
                    spot(3, "2026-01-20T12:00:00Z"),
                    spot(4, "2026-02-03T12:00:00Z"),
                ),
                madrid,
            )

        assertEquals(
            setOf(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 20)),
            calendar.daysIn(YearMonth.of(2026, 1)),
        )
        assertEquals(setOf(LocalDate.of(2026, 2, 3)), calendar.daysIn(YearMonth.of(2026, 2)))
        assertEquals(emptySet<LocalDate>(), calendar.daysIn(YearMonth.of(2026, 3)))
    }

    @Test
    fun `a month grid starts on the locale week and pads only the end`() {
        // February 2026 starts on a Sunday and has 28 days, so a Monday week leaves six holes.
        val february = YearMonth.of(2026, 2)

        val mondayWeeks = monthWeeks(february, DayOfWeek.MONDAY)
        assertEquals(5, mondayWeeks.size)
        assertEquals(listOf(null, null, null, null, null, null), mondayWeeks.first().dropLast(1))
        assertEquals(LocalDate.of(2026, 2, 1), mondayWeeks.first().last())
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 2, 24),
                LocalDate.of(2026, 2, 25),
                LocalDate.of(2026, 2, 26),
                LocalDate.of(2026, 2, 27),
                LocalDate.of(2026, 2, 28),
                null,
            ),
            mondayWeeks.last(),
        )

        val sundayWeeks = monthWeeks(february, DayOfWeek.SUNDAY)
        // Sunday the 1st to Saturday the 28th: four exact weeks, so no padding at all.
        assertEquals(4, sundayWeeks.size)
        assertEquals(LocalDate.of(2026, 2, 1), sundayWeeks.first().first())
    }

    @Test
    fun `a month that ends on the last day of the week gets no empty row`() {
        // February 2021 runs from Monday the 1st to Sunday the 28th.
        assertEquals(4, monthWeeks(YearMonth.of(2021, 2), DayOfWeek.MONDAY).size)
    }

    @Test
    fun `the week follows the locale`() {
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(Locale.UK))
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(Locale.US))
    }

    @Test
    fun `weekday labels follow the first day of the week`() {
        assertEquals(
            listOf("M", "T", "W", "T", "F", "S", "S"),
            weekdayLabels(DayOfWeek.MONDAY, Locale.UK),
        )
        assertEquals("S", weekdayLabels(DayOfWeek.SUNDAY, Locale.UK).first())
    }

    @Test
    fun `an empty history has no days`() {
        val calendar = ParkingCalendar(emptyList(), madrid)

        assertNull(calendar.newestDay)
        assertNull(calendar.oldestDay)
        assertEquals(emptySet<LocalDate>(), calendar.daysIn(YearMonth.of(2026, 1)))
    }
}
