package com.dicren.autospot.domain

import com.dicren.autospot.location.LocationFix
import com.dicren.autospot.location.LocationSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class LocateSelfUseCaseTest {
    private class RecordingSource(
        private val last: LocationFix? = null,
        private val current: LocationFix? = null,
        private val fail: Boolean = false,
    ) : LocationSource {
        var currentRequests = 0
            private set

        override suspend fun currentLocation(): LocationFix? {
            currentRequests++
            if (fail) throw IOException("no provider")
            return current
        }

        override suspend fun lastLocation(): LocationFix? {
            if (fail) throw IOException("no provider")
            return last
        }
    }

    private val cached = LocationFix(42.0, -5.0, timeMillis = 1_000, accuracyMeters = 10f)
    private val fresh = LocationFix(48.0, 2.0, timeMillis = 2_000, accuracyMeters = 5f)

    @Test
    fun `the cached fix is used without asking for a new one`() =
        runTest {
            val source = RecordingSource(last = cached, current = fresh)

            val fix = LocateSelfUseCase(source) { true }.find()

            assertEquals(cached, fix)
            assertEquals("A fresh fix should not be requested", 0, source.currentRequests)
        }

    @Test
    fun `a fresh fix is requested when nothing is cached`() =
        runTest {
            val source = RecordingSource(current = fresh)

            assertEquals(fresh, LocateSelfUseCase(source) { true }.find())
            assertEquals(1, source.currentRequests)
        }

    @Test
    fun `without the permission nothing is asked`() =
        runTest {
            val source = RecordingSource(last = cached)

            assertNull(LocateSelfUseCase(source) { false }.find())
            assertEquals(0, source.currentRequests)
        }

    @Test
    fun `a failing source is reported as no location`() =
        runTest {
            assertNull(LocateSelfUseCase(RecordingSource(fail = true)) { true }.find())
        }
}
