package com.dicren.autospot.ui.parking

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.parking.components.HistorySheet
import com.dicren.autospot.ui.parking.components.VehiclesSheet
import com.dicren.autospot.util.BluetoothDeviceInfo

/**
 * Every action the parking sheets can trigger. Grouping them keeps [ParkingSheets] readable as
 * the screen grows.
 */
data class ParkingSheetCallbacks(
    val onDismissVehicles: () -> Unit,
    val onDismissHistory: () -> Unit,
    val onCancelClearHistory: () -> Unit,
    val onSelectLocation: (ParkingLocationEntity) -> Unit,
    val onDeleteLocation: (ParkingLocationEntity) -> Unit,
    val onRequestClearHistory: () -> Unit,
    val onClearHistory: () -> Unit,
    /** Deletes only the group the overview is showing. */
    val onClearCluster: () -> Unit,
    /** Opens the overview on the active vehicle's spots. */
    val onShowVehicleOnMap: () -> Unit,
    /** Opens the overview on every vehicle's spots. */
    val onShowAllVehiclesOnMap: () -> Unit,
    val onActivateVehicle: (VehicleEntity) -> Unit,
    val onAddVehicle: (
        name: String,
        bluetoothAddress: String,
        bluetoothName: String,
        onResult: (Boolean) -> Unit,
    ) -> Unit,
    val onEditVehicle: (VehicleEntity, (Boolean) -> Unit) -> Unit,
    val onDeleteVehicle: (VehicleEntity) -> Unit,
)

/**
 * Every modal surface of the parking screen: the vehicle list, the history and the "clear all"
 * confirmation. Kept in its own file so [ParkingScreen] only orchestrates state and map overlays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingSheets(
    state: ParkingUiState,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    canReadBluetooth: Boolean,
    devices: List<BluetoothDeviceInfo>,
    showVehicles: Boolean,
    showHistory: Boolean,
    confirmClearHistory: Boolean,
    callbacks: ParkingSheetCallbacks,
) {
    if (showVehicles) {
        ModalBottomSheet(
            onDismissRequest = callbacks.onDismissVehicles,
        ) {
            VehiclesSheet(
                vehicles = state.vehicles,
                activeVehicle = state.activeVehicle,
                canReadBluetooth = canReadBluetooth,
                devices = devices,
                onVehicleClick = callbacks.onActivateVehicle,
                onAddVehicle = callbacks.onAddVehicle,
                onEditVehicle = callbacks.onEditVehicle,
                onDeleteVehicle = callbacks.onDeleteVehicle,
            )
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = callbacks.onDismissHistory,
        ) {
            HistorySheet(
                // The sheet follows the card: a group the map is showing lists only its spots.
                locations = state.historyLocations,
                timeFormatter = timeFormatter,
                nowMillis = nowMillis,
                onLocationSelected = callbacks.onSelectLocation,
                onDeleteLocation = callbacks.onDeleteLocation,
                onRequestClearAll = callbacks.onRequestClearHistory,
                vehicleNames = state.vehicleNames,
                showVehicleNames = state.isShowingAllVehicles,
                isClusterScoped = state.isClusterFocused,
                onShowOnMap = callbacks.onShowVehicleOnMap,
                onShowAllVehiclesOnMap =
                    if (state.vehicles.size > 1) callbacks.onShowAllVehiclesOnMap else null,
            )
        }
    }

    if (confirmClearHistory) {
        // A group of spots can span vehicles, so "every location of <vehicle>" would be a lie.
        val clearingCluster = state.isClusterFocused
        AlertDialog(
            onDismissRequest = callbacks.onCancelClearHistory,
            title = {
                Text(
                    stringResource(
                        if (clearingCluster) R.string.clear_group else R.string.clear_all,
                    ),
                )
            },
            text = {
                Text(
                    if (clearingCluster) {
                        stringResource(
                            R.string.clear_group_message,
                            state.historyLocations.size,
                        )
                    } else {
                        stringResource(
                            R.string.clear_all_message,
                            state.activeVehicle?.name.orEmpty(),
                        )
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (clearingCluster) callbacks.onClearCluster() else callbacks.onClearHistory()
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onCancelClearHistory) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
