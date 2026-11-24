package com.dicren.autospot.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens this app's system settings page, where permissions (including "Allow all the time" for
 * location) and battery settings live.
 */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
