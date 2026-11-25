package com.dicren.autospot.ui.parking

/** Localized snackbar text for each one-shot [ParkingEvent]. */
internal data class ParkingMessages(
    val saved: String,
    val saveFailed: String,
    val noVehicle: String,
    val permissionMissing: String,
    val duplicateVehicle: String,
    val updateConflict: String,
    val operationFailed: String,
) {
    fun forEvent(event: ParkingEvent): String =
        when (event) {
            ParkingEvent.LocationSaved -> saved
            ParkingEvent.SaveFailed -> saveFailed
            ParkingEvent.NoVehicle -> noVehicle
            ParkingEvent.PermissionMissing -> permissionMissing
            ParkingEvent.DuplicateVehicle -> duplicateVehicle
            ParkingEvent.VehicleUpdateConflict -> updateConflict
            ParkingEvent.OperationFailed -> operationFailed
        }
}
