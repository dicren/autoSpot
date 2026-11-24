package com.dicren.autospot.bluetooth

import com.dicren.autospot.core.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectionDebouncerTest {
    private class FakeClock(
        var now: Long = 0L,
    ) : Clock {
        override fun nowMillis(): Long = now
    }

    private val clock = FakeClock()
    private val debouncer = DisconnectionDebouncer(windowMillis = 10_000L, clock = clock)

    @Test
    fun `the first disconnection is handled`() =
        runTest {
            assertTrue(debouncer.tryAcquire(1L))
        }

    @Test
    fun `a repeat inside the window is ignored`() =
        runTest {
            assertTrue(debouncer.tryAcquire(1L))
            clock.now = 9_999L

            assertFalse(debouncer.tryAcquire(1L))
        }

    @Test
    fun `a repeat after the window is handled again`() =
        runTest {
            assertTrue(debouncer.tryAcquire(1L))
            clock.now = 10_000L

            assertTrue(debouncer.tryAcquire(1L))
        }

    @Test
    fun `vehicles debounce independently`() =
        runTest {
            assertTrue(debouncer.tryAcquire(1L))
            assertTrue(debouncer.tryAcquire(2L))
            assertFalse(debouncer.tryAcquire(1L))
        }

    @Test
    fun `only one of many concurrent events wins`() =
        runTest {
            val results =
                coroutineScope {
                    (1..25).map { async { debouncer.tryAcquire(42L) } }.awaitAll()
                }

            assertEquals(1, results.count { it })
        }
}
