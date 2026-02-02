package com.dicren.autospot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dicren.autospot.data.LocationRepository
import com.dicren.autospot.service.BluetoothMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed, checking if any vehicle is configured")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = LocationRepository(context)
                val vehicles = repository.getAllVehiclesSync()

                if (vehicles.isNotEmpty()) {
                    Log.d(TAG, "${vehicles.size} vehicle(s) configured, starting monitor service")
                    val serviceIntent = Intent(context, BluetoothMonitorService::class.java)
                    context.startForegroundService(serviceIntent)
                } else {
                    Log.d(TAG, "No vehicles configured, not starting service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking vehicle config", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
