package com.dicren.autospot.receiver

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.di.appContainer
import com.dicren.autospot.service.BluetoothMonitorService
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!isRelevantAction(intent.action)) return

        Log.d(TAG, "Device booted or app updated, checking if any vehicle is configured")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                startMonitorIfNeeded(
                    context = context,
                    vehicles = context.appContainer.vehicleRepository.getAllVehiclesOnce(),
                    canMonitor = Permissions.canMonitor(context),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error checking vehicle config", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * The system kills the app on an update, so it must also restart the monitor after
 * ACTION_MY_PACKAGE_REPLACED; otherwise auto-save silently stops until the app is opened.
 */
internal fun isRelevantAction(action: String?): Boolean =
    action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

/**
 * Starts the monitor when there is at least one vehicle and every permission is granted.
 *
 * Extracted from the receiver so the decision can be tested without a system broadcast.
 * Returns whether the foreground service was started.
 */
internal fun startMonitorIfNeeded(
    context: Context,
    vehicles: List<VehicleEntity>,
    canMonitor: Boolean,
): Boolean {
    if (vehicles.isEmpty() || !canMonitor) {
        Log.d(TAG, "No vehicles configured or permissions missing, not starting service")
        return false
    }

    Log.d(TAG, "${vehicles.size} vehicle(s) configured, starting monitor service")
    val serviceIntent = Intent(context, BluetoothMonitorService::class.java)

    return try {
        context.startForegroundService(serviceIntent)
        true
    } catch (e: ForegroundServiceStartNotAllowedException) {
        // Android 12+ can refuse a background foreground-service start. The monitor will be
        // started again the next time the app is opened.
        Log.w(TAG, "System refused to start the monitor after boot", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Missing permission to start the monitor after boot", e)
        false
    }
}
