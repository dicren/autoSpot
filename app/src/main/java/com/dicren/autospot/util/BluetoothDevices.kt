package com.dicren.autospot.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context

/** A paired Bluetooth device, reduced to what the add/edit vehicle dialog needs. */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
)

/** Reads the phone's paired devices, which requires BLUETOOTH_CONNECT on Android 12+. */
object BluetoothDevices {
    /**
     * Returns an empty list when the permission is missing or the adapter is unavailable:
     * `bondedDevices` throws `SecurityException` without BLUETOOTH_CONNECT, and the dialog must
     * keep working (it shows the permission notice instead).
     */
    @SuppressLint("MissingPermission")
    fun paired(
        context: Context,
        canReadBluetooth: Boolean,
    ): List<BluetoothDeviceInfo> {
        if (!canReadBluetooth) return emptyList()

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return runCatching {
            adapter
                ?.bondedDevices
                .orEmpty()
                .map { device ->
                    BluetoothDeviceInfo(
                        name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                        address = device.address,
                    )
                }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }
}
