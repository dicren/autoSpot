package com.dicren.autospot.core

/**
 * Source of wall-clock time. Injected everywhere a timestamp is taken or compared so tests can
 * control it instead of sleeping.
 */
fun interface Clock {
    fun nowMillis(): Long
}

object DefaultClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
