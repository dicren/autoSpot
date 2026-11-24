package com.dicren.autospot.ui.parking

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
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
 * Every modal surface of the parking screen: the vehicle list, the history and the "clear all"
 * confirmation. Kept in its own file so [ParkingScreen] only orchestrates state and map overlays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingSheets(
    state: ParkingUiState,
    timeFormatter: ParkingTimeFormatter,
    canReadBluetooth: Boolean,
    devices: List<BluetoothDeviceInfo>,
    showVehicles: Boolean,
    showHistory: Boolean,
    confirmClearHistory: Boolean,
    onDismissVehicles: () -> Unit,
    onDismissHistory: () -> Unit,
    onCancelClearHistory: () -> Unit,
    onSelectLocation: (ParkingLocationEntity) -> Unit,
    onDeleteLocation: (ParkingLocationEntity) -> Unit,
    onRequestClearHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onActivateVehicle: (VehicleEntity) -> Unit,
    onAddVehicle: (name: String, bluetoothAddress: String, bluetoothName: String) -> Unit,
    onEditVehicle: (VehicleEntity, (Boolean) -> Unit) -> Unit,
    onDeleteVehicle: (VehicleEntity) -> Unit,
) {
    val vehiclesSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val historySheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    if (showVehicles) {
        ModalBottomSheet(
            onDismissRequest = onDismissVehicles,
            sheetState = vehiclesSheetState,
        ) {
            VehiclesSheet(
                vehicles = state.vehicles,
                activeVehicle = state.activeVehicle,
                canReadBluetooth = canReadBluetooth,
                devices = devices,
                onVehicleClick = onActivateVehicle,
                onAddVehicle = onAddVehicle,
                onEditVehicle = onEditVehicle,
                onDeleteVehicle = onDeleteVehicle,
            )
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = onDismissHistory,
            sheetState = historySheetState,
        ) {
            HistorySheet(
                locations = state.locations,
                timeFormatter = timeFormatter,
                onLocationSelected = onSelectLocation,
                onDeleteLocation = onDeleteLocation,
                onRequestClearAll = onRequestClearHistory,
            )
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = onCancelClearHistory,
            title = { Text(stringResource(R.string.clear_all)) },
            text = {
                Text(
                    stringResource(
                        R.string.clear_all_message,
                        state.activeVehicle?.name.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onClearHistory) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelClearHistory) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
