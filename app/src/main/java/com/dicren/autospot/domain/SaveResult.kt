package com.dicren.autospot.domain

/** Outcome of an automatic or manual save, so the caller can tell the user what happened. */
sealed interface SaveResult {

    data class Saved(
        val vehicleId: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
    ) : SaveResult

    data object NoPermission : SaveResult

    data object NoVehicle : SaveResult

    /** Permission and vehicle were fine, but no usable fix was available. */
    data object NoFix : SaveResult

    data class Error(val cause: Throwable) : SaveResult
}
