package com.dicren.autospot.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothIntentsTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `ACL disconnection is a device disconnection`() {
        assertEquals(
            BluetoothEvent.DeviceDisconnected(address),
            BluetoothIntents.toEvent(BluetoothDevice.ACTION_ACL_DISCONNECTED, address),
        )
    }

    @Test
    fun `ACL connection is reported as connected`() {
        assertEquals(
            BluetoothEvent.DeviceConnected(address),
            BluetoothIntents.toEvent(BluetoothDevice.ACTION_ACL_CONNECTED, address),
        )
    }

    @Test
    fun `A2DP going from connected to disconnected is a disconnection`() {
        assertEquals(
            BluetoothEvent.DeviceDisconnected(address),
            BluetoothIntents.toEvent(
                action = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                deviceAddress = address,
                state = BluetoothProfile.STATE_DISCONNECTED,
                previousState = BluetoothProfile.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `A2DP disconnected twice in a row is ignored`() {
        assertNull(
            BluetoothIntents.toEvent(
                action = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                deviceAddress = address,
                state = BluetoothProfile.STATE_DISCONNECTED,
                previousState = BluetoothProfile.STATE_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `headset going from connected to disconnected is a disconnection`() {
        assertEquals(
            BluetoothEvent.DeviceDisconnected(address),
            BluetoothIntents.toEvent(
                action = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                deviceAddress = address,
                state = BluetoothProfile.STATE_DISCONNECTED,
                previousState = BluetoothProfile.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `adapter link going from connected to disconnected is a disconnection`() {
        assertEquals(
            BluetoothEvent.DeviceDisconnected(address),
            BluetoothIntents.toEvent(
                action = BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                deviceAddress = address,
                state = BluetoothProfile.STATE_DISCONNECTED,
                previousState = BluetoothProfile.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `an intermediate state is not a disconnection`() {
        assertNull(
            BluetoothIntents.toEvent(
                action = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                deviceAddress = address,
                state = BluetoothProfile.STATE_DISCONNECTING,
                previousState = BluetoothProfile.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `unknown actions are ignored`() {
        assertNull(BluetoothIntents.toEvent(BluetoothAdapter.ACTION_STATE_CHANGED, address))
        assertNull(BluetoothIntents.toEvent(null, address))
    }

    @Test
    fun `a missing address is passed through so the caller can ignore it`() {
        assertEquals(
            BluetoothEvent.DeviceDisconnected(null),
            BluetoothIntents.toEvent(BluetoothDevice.ACTION_ACL_DISCONNECTED, null),
        )
    }
}
