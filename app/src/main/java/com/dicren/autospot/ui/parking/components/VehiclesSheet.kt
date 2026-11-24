package com.dicren.autospot.ui.parking.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.VehicleEntity

@Composable
fun VehiclesSheet(
    vehicles: List<VehicleEntity>,
    activeVehicle: VehicleEntity?,
    canReadBluetooth: Boolean,
    onVehicleClick: (VehicleEntity) -> Unit,
    onAddVehicle: (name: String, bluetoothAddress: String, bluetoothName: String) -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onDeleteVehicle: (VehicleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var deletingVehicle by remember { mutableStateOf<VehicleEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.vehicles_title),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_vehicle),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (vehicles.isEmpty()) {
            Text(
                text = stringResource(R.string.no_vehicles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn {
                items(vehicles, key = { it.id }) { vehicle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVehicleClick(vehicle) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (vehicle.id == activeVehicle?.id) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.active),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text = vehicle.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = vehicle.bluetoothName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { editingVehicle = vehicle }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_vehicle),
                                )
                            }
                            IconButton(onClick = { deletingVehicle = vehicle }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showAddDialog) {
        VehicleEditDialog(
            vehicle = null,
            canReadBluetooth = canReadBluetooth,
            onDismiss = { showAddDialog = false },
            onSave = { name, bluetoothAddress, bluetoothName ->
                onAddVehicle(name, bluetoothAddress, bluetoothName)
                showAddDialog = false
            },
        )
    }

    editingVehicle?.let { vehicle ->
        VehicleEditDialog(
            vehicle = vehicle,
            canReadBluetooth = canReadBluetooth,
            onDismiss = { editingVehicle = null },
            onSave = { name, bluetoothAddress, bluetoothName ->
                onEditVehicle(
                    vehicle.copy(
                        name = name,
                        bluetoothAddress = bluetoothAddress,
                        bluetoothName = bluetoothName,
                    )
                )
                editingVehicle = null
            },
        )
    }

    deletingVehicle?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { deletingVehicle = null },
            title = { Text(stringResource(R.string.delete_vehicle)) },
            text = { Text(stringResource(R.string.delete_vehicle_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVehicle(vehicle)
                        deletingVehicle = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingVehicle = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
