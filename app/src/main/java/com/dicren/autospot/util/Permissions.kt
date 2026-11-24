package com.dicren.autospot.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** How much location precision the user has granted. */
enum class LocationAccuracy {
    NONE,
    COARSE,
    PRECISE,
}

/** Runtime permission checks shared by the UI, the services and the boot receiver. */
object Permissions {
    /**
     * Precise (`FINE`) wins over approximate (`COARSE`). Android 12+ lets the user pick
     * "Approximate" in the location dialog, and that is not enough to remember a parking spot.
     */
    fun locationAccuracy(context: Context): LocationAccuracy =
        when {
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccuracy.PRECISE
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccuracy.COARSE
            else -> LocationAccuracy.NONE
        }

    /** Only precise location is enough to save a parking spot. */
    fun hasLocation(context: Context): Boolean = locationAccuracy(context) == LocationAccuracy.PRECISE

    /** The user picked "Approximate" (or downgraded later in Settings). */
    fun isLocationApproximate(context: Context): Boolean = locationAccuracy(context) == LocationAccuracy.COARSE

    fun hasBackgroundLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasBluetoothConnect(context: Context): Boolean = granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    /** Everything the Bluetooth monitor needs to keep running and to save a location later. */
    fun canMonitor(context: Context): Boolean =
        hasLocation(context) && hasBackgroundLocation(context) && hasBluetoothConnect(context)

    /** Android 13+ requires a runtime grant before the ongoing notification is visible. */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
