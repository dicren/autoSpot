package com.dicren.autospot.ui.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingTimeFormatterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val formatter = ParkingTimeFormatter(context.resources)
    private val now = 1_000_000_000_000L

    @Test
    fun `less than a minute reads as just now`() {
        assertEquals("Just now", formatter.format(now - 59_000L, now))
    }

    @Test
    fun `a timestamp in the future still reads as just now`() {
        assertEquals("Just now", formatter.format(now + 60_000L, now))
    }

    @Test
    fun `one minute uses the singular plural`() {
        assertEquals("1 minute ago", formatter.format(now - 60_000L, now))
    }

    @Test
    fun `several minutes use the plural`() {
        assertEquals("25 minutes ago", formatter.format(now - 25 * 60_000L, now))
    }

    @Test
    fun `one hour uses the singular plural`() {
        assertEquals("1 hour ago", formatter.format(now - 60 * 60_000L, now))
    }

    @Test
    fun `twenty three hours still uses relative time`() {
        assertEquals("23 hours ago", formatter.format(now - 23 * 3_600_000L, now))
    }
}
