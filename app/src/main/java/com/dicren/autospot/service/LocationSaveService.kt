package com.dicren.autospot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dicren.autospot.R
import com.dicren.autospot.data.LocationRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationSaveService : Service() {

    companion object {
        private const val TAG = "LocationSaveService"
        private const val CHANNEL_ID = "parking_location_channel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationRepository: LocationRepository

    override fun onCreate() {
        super.onCreate()
        locationRepository = LocationRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        val vehicleId = intent?.getLongExtra(EXTRA_VEHICLE_ID, -1L) ?: -1L

        serviceScope.launch {
            try {
                saveCurrentLocation(vehicleId)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving location", e)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun saveCurrentLocation(vehicleId: Long) {
        // If no vehicleId provided, try to use active vehicle
        val finalVehicleId = if (vehicleId > 0) {
            vehicleId
        } else {
            val activeVehicle = locationRepository.getActiveVehicleSync()
            if (activeVehicle == null) {
                Log.w(TAG, "No vehicle specified and no active vehicle")
                return
            }
            activeVehicle.id
        }

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted")
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationToken = CancellationTokenSource()

        try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

            if (location != null) {
                locationRepository.saveLocation(finalVehicleId, location.latitude, location.longitude)
                Log.d(TAG, "Location saved for vehicle $finalVehicleId: ${location.latitude}, ${location.longitude}")
            } else {
                // Try to get last known location as fallback
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null) {
                    locationRepository.saveLocation(finalVehicleId, lastLocation.latitude, lastLocation.longitude)
                    Log.d(TAG, "Last known location saved for vehicle $finalVehicleId: ${lastLocation.latitude}, ${lastLocation.longitude}")
                } else {
                    Log.w(TAG, "No location available")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
