package com.dicren.autospot.util

/** Chooses the label shown for a vehicle when the user leaves the name empty. */
object VehicleName {

    fun resolve(userName: String, bluetoothName: String): String =
        userName.trim().ifEmpty { bluetoothName.trim() }
}
