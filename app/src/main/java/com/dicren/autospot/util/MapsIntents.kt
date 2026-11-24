package com.dicren.autospot.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Intents used to hand a saved location over to Google Maps or to another app. */
object MapsIntents {

    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun navigationUri(latitude: Double, longitude: Double): Uri =
        Uri.parse("google.navigation:q=$latitude,$longitude&mode=w")

    fun shareText(latitude: Double, longitude: Double): String =
        "https://maps.google.com/?q=$latitude,$longitude"

    fun navigationIntent(latitude: Double, longitude: Double): Intent =
        Intent(Intent.ACTION_VIEW, navigationUri(latitude, longitude))
            .setPackage(MAPS_PACKAGE)

    fun shareIntent(latitude: Double, longitude: Double, title: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText(latitude, longitude))
            },
            title,
        )

    /** Starts Maps, returning false when it is not installed instead of crashing the caller. */
    fun openNavigation(context: Context, latitude: Double, longitude: Double): Boolean =
        startNavigation(latitude, longitude) { intent -> context.startActivity(intent) }

    /**
     * The launch itself is injected so the missing-app path can be tested without a device.
     */
    fun startNavigation(
        latitude: Double,
        longitude: Double,
        startActivity: (Intent) -> Unit,
    ): Boolean = try {
        startActivity(navigationIntent(latitude, longitude))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
