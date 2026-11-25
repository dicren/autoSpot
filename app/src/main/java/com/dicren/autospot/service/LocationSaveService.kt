package com.dicren.autospot.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.dicren.autospot.di.appContainer
import com.dicren.autospot.domain.SaveParkingLocation
import com.dicren.autospot.domain.SaveResult
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-shot foreground service that saves a parking location.
 *
 * Saves are serialised so two starts cannot interleave, and each start stops only itself
 * (`stopSelfResult`) so an older request cannot kill a newer one.
 */
class LocationSaveService : Service() {
    private val serviceScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + applicationContext.appContainer.ioDispatcher)
    }
    private val saveMutex = Mutex()
    private lateinit var useCase: SaveParkingLocation
    private lateinit var notifications: AppNotifications

    override fun onCreate() {
        super.onCreate()
        notifications = AppNotifications(this)
        notifications.createChannels()

        useCase = applicationContext.appContainer.saveParkingLocationUseCase
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!Permissions.hasLocation(this)) {
            Log.w(TAG, "Location permission not granted, not saving")
            notifications.notifySaveFailed()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this,
                AppNotifications.SAVE_NOTIFICATION_ID,
                notifications.savingNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            // The system refuses a location-typed foreground service without permission, or when
            // started from the background without background location access.
            Log.e(TAG, "Cannot start foreground location service", e)
            notifications.notifySaveFailed()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Absent or non-positive means "the active vehicle".
        val vehicleId = intent?.getLongExtra(EXTRA_VEHICLE_ID, -1L)?.takeIf { it > 0 }

        serviceScope.launch {
            try {
                val result = saveMutex.withLock { useCase.save(vehicleId) }

                when (result) {
                    is SaveResult.Saved -> {
                        Log.d(
                            TAG,
                            "Location saved for vehicle ${result.vehicleId}: " +
                                "${result.latitude}, ${result.longitude} (accuracy ${result.accuracyMeters})",
                        )
                    }

                    SaveResult.NoPermission -> {
                        Log.w(TAG, "Location permission disappeared")
                        notifications.notifySaveFailed()
                    }

                    SaveResult.NoVehicle -> {
                        Log.w(TAG, "No vehicle to save the location for")
                    }

                    SaveResult.NoFix -> {
                        Log.w(TAG, "No usable location fix")
                        notifications.notifySaveFailed()
                    }

                    is SaveResult.Error -> {
                        Log.e(TAG, "Could not save the location", result.cause)
                        notifications.notifySaveFailed()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The use case is total, but a future regression must never leave the foreground
                // service (and its "saving" notification) alive forever.
                Log.e(TAG, "Unexpected error while saving the location", e)
                notifications.notifySaveFailed()
            } finally {
                stopSelfResult(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationSaveService"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }
}
