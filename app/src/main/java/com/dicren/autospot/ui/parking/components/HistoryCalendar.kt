package com.dicren.autospot.ui.parking.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.components.TooltippedIconButton
import com.dicren.autospot.ui.format.ParkingCalendar
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.format.firstDayOfWeek
import com.dicren.autospot.ui.format.monthWeeks
import com.dicren.autospot.ui.format.weekdayLabels
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Identifies the calendar face of the history sheet, so tests do not depend on month names. */
internal const val HISTORY_CALENDAR_TAG = "history_calendar"

/** The grid that pages months with a sideways swipe. */
internal const val CALENDAR_GRID_TAG = "history_calendar_grid"

/** `history_calendar_day_<epochDay>` on every day cell. */
internal const val CALENDAR_DAY_TAG_PREFIX = "history_calendar_day_"

/** `history_calendar_mark_<epochDay>` on the dot of a day that holds at least one spot. */
internal const val CALENDAR_MARK_TAG_PREFIX = "history_calendar_mark_"

/** No day selected: a month with no spots has nothing to list. */
private const val NO_DAY = -1L

/** How far a finger has to travel sideways before the grid treats it as a page turn. */
private val SWIPE_THRESHOLD = 64.dp

/** Long enough to read as a slide, short enough not to keep the finger waiting. */
private const val MONTH_ANIMATION_MILLIS = 220

/**
 * The calendar face of the history: a month grid with a dot on every day that holds a spot, and
 * that day's spots underneath.
 *
 * The sheet opens on the newest spot's month and day. Months change with the arrows or with a
 * sideways swipe on the grid, and the grid slides in the direction the user is travelling, so the
 * gesture and the buttons tell the same story. Both stop at the months that hold the oldest and
 * newest spots: paging through months that can never list anything would only ask the user to
 * guess which one they want.
 *
 * Tapping a marked day reports it through [onRevealEntries] as well as selecting it: the day's
 * spots sit under the grid, and in a half-expanded sheet they would be off-screen, which reads as
 * "nothing happened".
 */
@Composable
internal fun HistoryCalendar(
    calendar: ParkingCalendar,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    onLocationSelected: (ParkingLocationEntity) -> Unit,
    onDeleteLocation: (ParkingLocationEntity) -> Unit,
    vehicleNames: Map<Long, String>,
    showVehicleNames: Boolean,
    onRevealEntries: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { firstDayOfWeek(locale) }
    val weekdays = remember(firstDayOfWeek, locale) { weekdayLabels(firstDayOfWeek, locale) }

    val newestDay = calendar.newestDay ?: return
    val newestMonth = remember(newestDay) { YearMonth.from(newestDay) }
    val oldestMonth = remember(calendar, newestMonth) { calendar.oldestDay?.let(YearMonth::from) ?: newestMonth }

    var monthEpoch by rememberSaveable { mutableLongStateOf(newestDay.withDayOfMonth(1).toEpochDay()) }
    var selectedEpoch by rememberSaveable { mutableLongStateOf(newestDay.toEpochDay()) }

    val month = remember(monthEpoch) { YearMonth.from(LocalDate.ofEpochDay(monthEpoch)) }
    val markedDays = remember(calendar, month) { calendar.daysIn(month) }
    val selectedDay =
        remember(selectedEpoch, month) {
            LocalDate.ofEpochDay(selectedEpoch).takeIf { YearMonth.from(it) == month }
        }
    val spots = selectedDay?.let { calendar.spotsByDay[it] }.orEmpty()
    val canGoBack = month > oldestMonth
    val canGoForward = month < newestMonth

    val monthPattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "yMMMM") }
    val monthTitle =
        remember(month, monthPattern, locale) {
            month.atDay(1).format(DateTimeFormatter.ofPattern(monthPattern, locale))
        }
    val dayFormatter =
        remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    fun showMonth(target: YearMonth) {
        if (target < oldestMonth || target > newestMonth) return
        monthEpoch = target.atDay(1).toEpochDay()
        // Landing on the month's newest spot keeps the list useful; an empty month has no day.
        selectedEpoch = calendar.daysIn(target).maxOrNull()?.toEpochDay() ?: NO_DAY
    }

    val swipeThreshold = with(LocalDensity.current) { SWIPE_THRESHOLD.roundToPx() }
    val pageWithSwipe =
        Modifier.pointerInput(month, canGoBack, canGoForward) {
            var travelled = 0f
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    // The calendar owns horizontal movement, so a sideways swipe over a spot row
                    // pages the month instead of opening the spot. Vertical stays with the list
                    // and the sheet, which is what keeps the sheet draggable while paging.
                    change.consume()
                    travelled += dragAmount
                },
                onDragEnd = {
                    when {
                        travelled <= -swipeThreshold && canGoForward -> showMonth(month.plusMonths(1))
                        travelled >= swipeThreshold && canGoBack -> showMonth(month.minusMonths(1))
                    }
                    travelled = 0f
                },
                onDragCancel = { travelled = 0f },
            )
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(HISTORY_CALENDAR_TAG)
                .then(pageWithSwipe),
    ) {
        item(key = "month") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TooltippedIconButton(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = stringResource(R.string.previous_month),
                    onClick = { showMonth(month.minusMonths(1)) },
                    enabled = canGoBack,
                )
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TooltippedIconButton(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = stringResource(R.string.next_month),
                    onClick = { showMonth(month.plusMonths(1)) },
                    enabled = canGoForward,
                )
            }
        }

        item(key = "weekdays") {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        item(key = "grid") {
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter =
                        slideInHorizontally(tween(MONTH_ANIMATION_MILLIS)) { width ->
                            if (forward) width else -width
                        } + fadeIn(tween(MONTH_ANIMATION_MILLIS))
                    val exit =
                        slideOutHorizontally(tween(MONTH_ANIMATION_MILLIS)) { width ->
                            if (forward) -width else width
                        } + fadeOut(tween(MONTH_ANIMATION_MILLIS))
                    enter togetherWith exit
                },
                modifier = Modifier.fillMaxWidth().testTag(CALENDAR_GRID_TAG),
                label = "history_calendar_month",
            ) { shownMonth ->
                // Marks and selection are computed per page: during the slide both months are on
                // screen, and the outgoing one must not borrow the incoming one's dots.
                val pageDays = remember(calendar, shownMonth) { calendar.daysIn(shownMonth) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    monthWeeks(shownMonth, firstDayOfWeek).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                DayCell(
                                    day = day,
                                    hasSpots = day != null && day in pageDays,
                                    isSelected = day == selectedDay,
                                    isToday = day == LocalDate.now(),
                                    onSelect = {
                                        selectedEpoch = it.toEpochDay()
                                        onRevealEntries()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (markedDays.isEmpty()) {
            item(key = "empty_month") {
                EmptyCalendarText(stringResource(R.string.no_spots_this_month))
            }
        } else {
            item(key = "selected_day") {
                Text(
                    text = selectedDay?.format(dayFormatter).orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            if (spots.isEmpty()) {
                item(key = "empty_day") {
                    EmptyCalendarText(stringResource(R.string.no_spots_this_day))
                }
            }
            items(spots, key = { it.id }) { location ->
                HistoryRow(
                    location = location,
                    timeFormatter = timeFormatter,
                    nowMillis = nowMillis,
                    vehicleNames = vehicleNames,
                    showVehicleName = showVehicleNames,
                    onLocationSelected = onLocationSelected,
                    onDeleteLocation = onDeleteLocation,
                )
            }
        }
    }
}

/**
 * One day of the grid. The whole 44 dp cell is the touch target; only the days that hold spots
 * react, because selecting an empty day would only empty the list under the grid.
 */
@Composable
private fun RowScope.DayCell(
    day: LocalDate?,
    hasSpots: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(44.dp)
                .then(
                    if (day != null) {
                        Modifier
                            .testTag("$CALENDAR_DAY_TAG_PREFIX${day.toEpochDay()}")
                            .clickable(enabled = hasSpots) { onSelect(day) }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (day == null) return@Box
        val background =
            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        val content =
            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(background)
                    .then(
                        // Today keeps a ring until it is the selected day, which already fills it.
                        if (isToday && !isSelected) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            if (hasSpots) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) content else MaterialTheme.colorScheme.primary)
                            .testTag("$CALENDAR_MARK_TAG_PREFIX${day.toEpochDay()}"),
                )
            }
        }
    }
}

@Composable
private fun EmptyCalendarText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
    )
}
