package com.dicren.autospot.ui.format

import android.content.res.Resources
import com.dicren.autospot.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a parking timestamp as human text.
 *
 * Time is injected instead of read from the system clock, and the date fallback follows the
 * app's locale instead of a hard-coded `dd/MM/yyyy` pattern.
 */
class ParkingTimeFormatter(
    private val resources: Resources,
) {
    fun format(
        timestamp: Long,
        now: Long,
    ): String {
        val diff = now - timestamp
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L

        return when {
            minutes < 1 -> {
                resources.getString(R.string.just_now)
            }

            hours < 1 -> {
                resources.getQuantityString(
                    R.plurals.minutes_ago,
                    minutes.toInt(),
                    minutes.toInt(),
                )
            }

            hours < 24 -> {
                resources.getQuantityString(
                    R.plurals.hours_ago,
                    hours.toInt(),
                    hours.toInt(),
                )
            }

            else -> {
                dateFormat().format(Date(timestamp))
            }
        }
    }

    private fun dateFormat(): DateFormat {
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        // MEDIUM keeps the year; SHORT would render "9/9/01" and lose it.
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
}
