package com.dicren.autospot.ui.parking.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.util.VehicleName

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
)

@Composable
fun VehicleEditDialog(
    vehicle: VehicleEntity?,
    canReadBluetooth: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, bluetoothAddress: String, bluetoothName: String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vehicle?.name ?: "") }
    var selectedBluetooth by remember {
        mutableStateOf(
            vehicle?.let { BluetoothDeviceInfo(it.bluetoothName, it.bluetoothAddress) },
        )
    }
    var showBluetoothPicker by remember { mutableStateOf(false) }

    val pairedDevices = remember(canReadBluetooth) { readPairedDevices(context, canReadBluetooth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (vehicle == null) R.string.add_vehicle else R.string.edit_vehicle),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.vehicle_name)) },
                    placeholder = { Text(stringResource(R.string.vehicle_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.bluetooth_device),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    !canReadBluetooth -> {
                        BluetoothPermissionNotice(onGrantClick = {
                            context.openAppSettings()
                        })
                    }

                    showBluetoothPicker -> {
                        BluetoothDevicePicker(
                            devices = pairedDevices,
                            onSelect = { device ->
                                selectedBluetooth = device
                                showBluetoothPicker = false
                            },
                        )
                    }

                    else -> {
                        OutlinedButton(
                            onClick = { showBluetoothPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text =
                                    selectedBluetooth?.name
                                        ?: stringResource(R.string.select_bluetooth_device),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedBluetooth?.let { bluetooth ->
                        onSave(
                            VehicleName.resolve(name, bluetooth.name),
                            bluetooth.address,
                            bluetooth.name,
                        )
                    }
                },
                enabled = selectedBluetooth != null,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun BluetoothPermissionNotice(onGrantClick: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.bluetooth_permission_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onGrantClick) {
            Text(stringResource(R.string.grant_permissions))
        }
    }
}

@Composable
private fun BluetoothDevicePicker(
    devices: List<BluetoothDeviceInfo>,
    onSelect: (BluetoothDeviceInfo) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        LazyColumn(modifier = Modifier.height(200.dp)) {
            if (devices.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_paired_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(devices, key = { it.address }) { device ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(device) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Reading `bondedDevices` throws SecurityException without BLUETOOTH_CONNECT. Catching it keeps
 * the add-vehicle dialog from crashing when the permission was denied.
 */
@SuppressLint("MissingPermission")
private fun readPairedDevices(
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

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
