package com.dicren.autospot.bluetooth

import com.dicren.autospot.core.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate-limits disconnection handling per vehicle.
 *
 * Car head units usually emit several broadcasts in a burst (ACL, A2DP, headset). Without this,
 * each one would start a location save. The debounce state used to live in a plain
 * [MutableMap] written from concurrent coroutines; the mutex makes the check-and-record
 * atomic and safe.
 */
class DisconnectionDebouncer(
    private val windowMillis: Long,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val lastHandledAt = mutableMapOf<Long, Long>()

    /** Returns true when the event should be handled, and records the time. */
    suspend fun tryAcquire(vehicleId: Long): Boolean =
        mutex.withLock {
            val now = clock.nowMillis()
            val last = lastHandledAt[vehicleId]
            if (last != null && now - last < windowMillis) {
                false
            } else {
                lastHandledAt[vehicleId] = now
                true
            }
        }
}
