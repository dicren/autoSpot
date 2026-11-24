package com.dicren.autospot.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.dicren.autospot.MainActivity
import com.dicren.autospot.R
import com.dicren.autospot.bluetooth.BluetoothEvent
import com.dicren.autospot.bluetooth.BluetoothIntents
import com.dicren.autospot.bluetooth.DisconnectionDebouncer
import com.dicren.autospot.core.DefaultClock
import com.dicren.autospot.data.LocationRepository
import com.dicren.autospot.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BluetoothMonitorService : Service() {

    companion object {
        private const val TAG = "BluetoothMonitorService"
        private const val CHANNEL_ID = "bluetooth_monitor_channel_v2"
        private const val LEGACY_CHANNEL_ID = "bluetooth_monitor_channel"
        private const val NOTIFICATION_ID = 3
        private const val DEBOUNCE_TIME_MS = 10_000L // 10 seconds
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: LocationRepository
    private val debouncer = DisconnectionDebouncer(DEBOUNCE_TIME_MS, DefaultClock)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "Bluetooth event received: $action")

            val device = IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )

            val deviceName = try { device?.name } catch (e: SecurityException) { "unknown" }
            val deviceAddress = device?.address

            // The adapter broadcast uses its own extras; profiles and ACL use the profile ones.
            val isAdapterEvent = action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED
            val state = if (isAdapterEvent) {
                intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, Int.MIN_VALUE)
            } else {
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, Int.MIN_VALUE)
            }
            val previousState = if (isAdapterEvent) {
                intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, Int.MIN_VALUE)
            } else {
                intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, Int.MIN_VALUE)
            }

            Log.d(TAG, "Event: $action, $deviceName ($deviceAddress), state: $previousState -> $state")

            when (val event = BluetoothIntents.toEvent(action, deviceAddress, state, previousState)) {
                is BluetoothEvent.DeviceDisconnected -> handleDisconnection(event.deviceAddress)
                is BluetoothEvent.DeviceConnected, null -> Unit
            }
        }
    }

    private fun handleDisconnection(deviceAddress: String?) {
        if (deviceAddress == null) return

        serviceScope.launch {
            try {
                val vehicle = repository.getVehicleByBluetoothAddress(deviceAddress)

                if (vehicle != null) {
                    if (!debouncer.tryAcquire(vehicle.id)) {
                        Log.d(TAG, "Ignoring disconnection event for '${vehicle.name}' (debounce)")
                        return@launch
                    }

                    Log.d(TAG, "Vehicle '${vehicle.name}' Bluetooth disconnected! Saving location...")

                    val serviceIntent = Intent(this@BluetoothMonitorService, LocationSaveService::class.java).apply {
                        putExtra(LocationSaveService.EXTRA_VEHICLE_ID, vehicle.id)
                    }
                    startForegroundService(serviceIntent)
                } else {
                    Log.d(TAG, "Disconnected device $deviceAddress is not a configured vehicle")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling disconnection", e)
            }
        }
    }

    @SuppressLint("InlinedApi") // RECEIVER_EXPORTED is inlined (value 2), safe on API 31+.
    override fun onCreate() {
        super.onCreate()
        repository = LocationRepository(applicationContext)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)

        Log.d(TAG, "BluetoothMonitorService created and receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Permissions.canMonitor(this)) {
            Log.w(TAG, "Location or Bluetooth permission missing, stopping monitor")
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            START_STICKY
        } catch (e: SecurityException) {
            // The system refuses a location-typed foreground service without permission.
            Log.e(TAG, "Cannot start foreground monitor", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        serviceScope.cancel()
        Log.d(TAG, "BluetoothMonitorService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        // A new channel id is required to apply a lower importance to existing installs:
        // Android ignores importance changes on channels that already exist.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bluetooth_monitor_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.bluetooth_monitor_channel_description)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bluetooth_monitor_notification_title))
            .setContentText(getString(R.string.bluetooth_monitor_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()
    }
}
