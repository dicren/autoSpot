package com.dicren.autospot.bluetooth

/**
 * A Bluetooth broadcast reduced to the only thing this app cares about: whether a configured
 * device just lost its connection, and which one.
 */
sealed interface BluetoothEvent {

    /** The device lost its ACL link, its audio profile or the adapter link to it. */
    data class DeviceDisconnected(val deviceAddress: String?) : BluetoothEvent

    /** The device established a link. Currently unused, but keeps the mapper total. */
    data class DeviceConnected(val deviceAddress: String?) : BluetoothEvent
}
