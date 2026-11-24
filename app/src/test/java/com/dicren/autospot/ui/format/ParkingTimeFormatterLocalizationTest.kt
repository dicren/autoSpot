package com.dicren.autospot.ui.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class ParkingTimeFormatterLocalizationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val formatter = ParkingTimeFormatter(context.resources)
    private val now = 1_000_000_000_000L

    @Test
    fun `relative time is translated`() {
        assertEquals("Hace 5 minutos", formatter.format(now - 5 * 60_000L, now))
    }

    @Test
    fun `the date fallback follows the app locale`() {
        val timestamp = now - 48 * 3_600_000L
        val year = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

        val formatted = formatter.format(timestamp, now)

        assertTrue("Expected a Spanish date in '$formatted'", formatted.contains(year.toString()))
        assertFalse("Expected an absolute date, got '$formatted'", formatted.startsWith("Hace"))
    }
}
