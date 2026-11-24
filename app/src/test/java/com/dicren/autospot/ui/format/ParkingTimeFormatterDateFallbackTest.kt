package com.dicren.autospot.ui.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingTimeFormatterDateFallbackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val formatter = ParkingTimeFormatter(context.resources)
    private val now = 1_000_000_000_000L

    @Test
    fun `a day or more is rendered as an absolute date`() {
        val timestamp = now - 30 * 3_600_000L
        val year = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

        val formatted = formatter.format(timestamp, now)

        assertTrue("Expected the year in '$formatted'", formatted.contains(year.toString()))
        assertFalse(formatted.contains("ago"))
    }
}
