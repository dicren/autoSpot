package com.dicren.autospot.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile

/**
 * Maps a Bluetooth broadcast to a [BluetoothEvent] without touching the framework.
 *
 * The broadcast receiver only has to pull four primitives out of the [android.content.Intent]
 * (action, device address, state and previous state); the decision of whether that means
 * "the car disconnected" lives here so it can be unit tested.
 */
object BluetoothIntents {

    fun toEvent(
        action: String?,
        deviceAddress: String?,
        state: Int = Int.MIN_VALUE,
        previousState: Int = Int.MIN_VALUE,
    ): BluetoothEvent? = when (action) {
        BluetoothDevice.ACTION_ACL_DISCONNECTED -> BluetoothEvent.DeviceDisconnected(deviceAddress)

        BluetoothDevice.ACTION_ACL_CONNECTED -> BluetoothEvent.DeviceConnected(deviceAddress)

        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
        -> when {
            state == BluetoothProfile.STATE_CONNECTED -> BluetoothEvent.DeviceConnected(deviceAddress)
            // Only a real transition matters: some stacks deliver repeated DISCONNECTED states.
            state == BluetoothProfile.STATE_DISCONNECTED &&
                previousState == BluetoothProfile.STATE_CONNECTED ->
                BluetoothEvent.DeviceDisconnected(deviceAddress)

            else -> null
        }

        else -> null
    }
}
