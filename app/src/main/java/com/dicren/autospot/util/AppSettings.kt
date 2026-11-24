package com.dicren.autospot.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "AppSettings"

/**
 * Opens this app's system settings page, where permissions (including "Allow all the time" for
 * location) and battery settings live.
 *
 * The page is not guaranteed to exist (some TV/automotive builds omit it), so a failure falls
 * back to the general settings screen instead of crashing the app.
 */
fun Context.openAppSettings() {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app can show the application details page", e)
        openGeneralSettings()
    } catch (e: SecurityException) {
        Log.w(TAG, "The system refused to show the application details page", e)
        openGeneralSettings()
    }
}

private fun Context.openGeneralSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app can show the system settings", e)
    } catch (e: SecurityException) {
        Log.w(TAG, "The system refused to show the system settings", e)
    }
}
