package com.dicren.autospot.location

/** A location fix, decoupled from Play Services so the save logic is testable. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val accuracyMeters: Float?,
)

interface LocationSource {

    /**
     * Requests a fresh fix. Returns null when no fix could be obtained within the timeout.
     */
    suspend fun currentLocation(): LocationFix?

    /** Last known fix, which may be old. */
    suspend fun lastLocation(): LocationFix?
}
