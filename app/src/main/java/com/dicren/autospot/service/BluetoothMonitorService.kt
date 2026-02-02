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
import com.dicren.autospot.MainActivity
import com.dicren.autospot.R
import com.dicren.autospot.data.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BluetoothMonitorService : Service() {

    companion object {
        private const val TAG = "BluetoothMonitorService"
        private const val CHANNEL_ID = "bluetooth_monitor_channel"
        private const val NOTIFICATION_ID = 3
        private const val DEBOUNCE_TIME_MS = 10_000L // 10 seconds
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: LocationRepository
    private val lastDisconnectionTimeByVehicle = mutableMapOf<Long, Long>()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Bluetooth event received: ${intent.action}")

            val device = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )

            val deviceName = try { device?.name } catch (e: SecurityException) { "unknown" }
            val deviceAddress = device?.address

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "ACL Disconnected: $deviceName ($deviceAddress)")
                    handleDisconnection(deviceAddress)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    val prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
                    Log.d(TAG, "Profile state changed: $deviceName ($deviceAddress), state: $prevState -> $state")

                    if (state == BluetoothProfile.STATE_DISCONNECTED &&
                        prevState == BluetoothProfile.STATE_CONNECTED) {
                        handleDisconnection(deviceAddress)
                    }
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                    val prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, -1)
                    Log.d(TAG, "Adapter connection state: $deviceName ($deviceAddress), state: $prevState -> $state")

                    if (state == BluetoothAdapter.STATE_DISCONNECTED &&
                        prevState == BluetoothAdapter.STATE_CONNECTED) {
                        handleDisconnection(deviceAddress)
                    }
                }
            }
        }
    }

    private fun handleDisconnection(deviceAddress: String?) {
        if (deviceAddress == null) return

        serviceScope.launch {
            try {
                val vehicle = repository.getVehicleByBluetoothAddress(deviceAddress)

                if (vehicle != null) {
                    val now = System.currentTimeMillis()
                    val lastTime = lastDisconnectionTimeByVehicle[vehicle.id] ?: 0L

                    if (now - lastTime < DEBOUNCE_TIME_MS) {
                        Log.d(TAG, "Ignoring disconnection event for '${vehicle.name}' (debounce)")
                        return@launch
                    }

                    lastDisconnectionTimeByVehicle[vehicle.id] = now
                    Log.d(TAG, "Vehicle '${vehicle.name}' Bluetooth disconnected! Saving location...")

                    val serviceIntent = Intent(this@BluetoothMonitorService, LocationSaveService::class.java).apply {
                        putExtra(EXTRA_VEHICLE_ID, vehicle.id)
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
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        return START_STICKY
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bluetooth_monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.bluetooth_monitor_channel_description)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
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
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
