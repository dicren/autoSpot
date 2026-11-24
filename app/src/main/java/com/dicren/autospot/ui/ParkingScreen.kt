package com.dicren.autospot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.LocationRepository
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.service.BluetoothMonitorService
import com.dicren.autospot.service.LocationSaveService
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.util.MapsIntents
import com.dicren.autospot.util.Permissions
import com.dicren.autospot.util.VehicleName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

data class BluetoothDeviceInfo(
    val name: String,
    val address: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notifications are optional: the services run without them. */ }

    val hasPermissions = remember { mutableStateOf(Permissions.canMonitor(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check permissions when resuming
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasPermissions.value = Permissions.canMonitor(context)
        }
    }
    val locationRepository = remember { LocationRepository(context) }
    val latestLocation by locationRepository.latestParkingLocation.collectAsState(initial = null)
    val allLocations by locationRepository.allParkingLocations.collectAsState(initial = emptyList())
    val activeVehicle by locationRepository.activeVehicle.collectAsState(initial = null)
    val allVehicles by locationRepository.allVehicles.collectAsState(initial = emptyList())

    var showVehiclesSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<ParkingLocationEntity?>(null) }
    val vehiclesSheetState = rememberModalBottomSheetState()
    val historySheetState = rememberModalBottomSheetState()

    val displayLocation = selectedLocation ?: latestLocation
    val coroutineScope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.share_location)
    val timeFormatter = remember { ParkingTimeFormatter(context.resources) }

    val cameraPositionState = rememberCameraPositionState()

    // Start/stop bluetooth monitor service based on vehicles and granted permissions
    LaunchedEffect(allVehicles, hasPermissions.value) {
        if (allVehicles.isNotEmpty() && hasPermissions.value) {
            try {
                context.startForegroundService(Intent(context, BluetoothMonitorService::class.java))
            } catch (e: Exception) {
                Log.w("ParkingScreen", "Could not start Bluetooth monitor", e)
            }
        } else {
            context.stopService(Intent(context, BluetoothMonitorService::class.java))
        }
    }

    // Center map when displayLocation changes (on start or when selecting from history)
    LaunchedEffect(displayLocation) {
        displayLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(it.latitude, it.longitude),
                    17f
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Map as background (full screen)
        if (displayLocation != null) {
            val position = LatLng(displayLocation.latitude, displayLocation.longitude)

            @SuppressLint("MissingPermission")
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    // Only request the blue dot when the permission is granted: the Maps SDK
                    // throws SecurityException otherwise (e.g. after revoking it in Settings).
                    isMyLocationEnabled = Permissions.hasLocation(context)
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                Marker(
                    state = rememberUpdatedMarkerState(position = position),
                    title = stringResource(R.string.marker_title)
                )
            }
        }

        // Top cards container
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            // Permission warning banner
            if (!hasPermissions.value) {
                PermissionBanner(
                    onGrantClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Floating Vehicle card
            VehicleCard(
                vehicle = activeVehicle,
                onSettingsClick = { showVehiclesSheet = true }
            )
        }

        // Compass button to reset map orientation (only visible when rotated)
        AnimatedVisibility(
            visible = cameraPositionState.position.bearing != 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(cameraPositionState.position.target)
                                    .zoom(cameraPositionState.position.zoom)
                                    .bearing(0f)
                                    .tilt(0f)
                                    .build()
                            )
                        )
                    }
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = stringResource(R.string.reset_orientation),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Content based on location state
        if (displayLocation != null) {
            // Floating navigate button (bottom right, above the card)
            FloatingActionButton(
                onClick = {
                    val opened = MapsIntents.openNavigation(
                        context,
                        displayLocation.latitude,
                        displayLocation.longitude,
                    )
                    if (!opened) {
                        Toast.makeText(context, R.string.maps_not_found, Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 140.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.navigate_button),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Floating bottom card with info and actions
            FloatingLocationCard(
                location = displayLocation,
                timeFormatter = timeFormatter,
                historyCount = allLocations.size,
                isFromHistory = selectedLocation != null,
                onShareClick = {
                    context.startActivity(
                        MapsIntents.shareIntent(
                            displayLocation.latitude,
                            displayLocation.longitude,
                            shareTitle,
                        )
                    )
                },
                onHistoryClick = { showHistorySheet = true },
                onBackToLatestClick = { selectedLocation = null },
                onCenterMapClick = {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(displayLocation.latitude, displayLocation.longitude),
                                17f
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        } else {
            // No location - show message and save button
            NoLocationContent(
                onSaveLocationClick = {
                    val serviceIntent = Intent(context, LocationSaveService::class.java)
                    context.startForegroundService(serviceIntent)
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    // Vehicles bottom sheet
    if (showVehiclesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVehiclesSheet = false },
            sheetState = vehiclesSheetState
        ) {
            VehiclesSheet(
                vehicles = allVehicles,
                activeVehicle = activeVehicle,
                onVehicleClick = { vehicle ->
                    CoroutineScope(Dispatchers.IO).launch {
                        locationRepository.setActiveVehicle(vehicle.id)
                    }
                },
                onAddVehicle = { name, bluetoothAddress, bluetoothName ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val id = locationRepository.addVehicle(name, bluetoothAddress, bluetoothName)
                        locationRepository.setActiveVehicle(id)
                    }
                    // Ask for notification permission in context, when monitoring first starts.
                    if (allVehicles.isEmpty() && Permissions.needsNotificationPermission(context)) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onEditVehicle = { vehicle ->
                    CoroutineScope(Dispatchers.IO).launch {
                        locationRepository.updateVehicle(vehicle)
                    }
                },
                onDeleteVehicle = { vehicle ->
                    CoroutineScope(Dispatchers.IO).launch {
                        locationRepository.deleteVehicle(vehicle.id)
                    }
                }
            )
        }
    }

    // History bottom sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = historySheetState
        ) {
            HistoryList(
                locations = allLocations,
                timeFormatter = timeFormatter,
                onLocationSelected = { location ->
                    selectedLocation = location
                    showHistorySheet = false
                },
                onDeleteLocation = { location ->
                    CoroutineScope(Dispatchers.IO).launch {
                        locationRepository.deleteLocation(location.id)
                    }
                    if (selectedLocation?.id == location.id) {
                        selectedLocation = null
                    }
                },
                onClearAll = {
                    activeVehicle?.let { vehicle ->
                        CoroutineScope(Dispatchers.IO).launch {
                            locationRepository.clearAllLocations(vehicle.id)
                        }
                    }
                    selectedLocation = null
                    showHistorySheet = false
                }
            )
        }
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleEntity?,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vehicle?.name ?: stringResource(R.string.no_vehicle_configured),
                style = MaterialTheme.typography.titleMedium,
                color = if (vehicle != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.vehicles_title)
                )
            }
        }
    }
}

@Composable
private fun VehiclesSheet(
    vehicles: List<VehicleEntity>,
    activeVehicle: VehicleEntity?,
    onVehicleClick: (VehicleEntity) -> Unit,
    onAddVehicle: (name: String, bluetoothAddress: String, bluetoothName: String) -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onDeleteVehicle: (VehicleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.vehicles_title),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_vehicle)
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
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn {
                items(vehicles) { vehicle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVehicleClick(vehicle) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (vehicle.id == activeVehicle?.id) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.active),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text = vehicle.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = vehicle.bluetoothName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { editingVehicle = vehicle }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_vehicle)
                                )
                            }
                            IconButton(onClick = { onDeleteVehicle(vehicle) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
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
            onDismiss = { showAddDialog = false },
            onSave = { name, bluetoothAddress, bluetoothName ->
                onAddVehicle(name, bluetoothAddress, bluetoothName)
                showAddDialog = false
            }
        )
    }

    editingVehicle?.let { vehicle ->
        VehicleEditDialog(
            vehicle = vehicle,
            onDismiss = { editingVehicle = null },
            onSave = { name, bluetoothAddress, bluetoothName ->
                onEditVehicle(vehicle.copy(
                    name = name,
                    bluetoothAddress = bluetoothAddress,
                    bluetoothName = bluetoothName
                ))
                editingVehicle = null
            }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun VehicleEditDialog(
    vehicle: VehicleEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, bluetoothAddress: String, bluetoothName: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vehicle?.name ?: "") }
    var selectedBluetooth by remember {
        mutableStateOf(
            vehicle?.let { BluetoothDeviceInfo(it.bluetoothName, it.bluetoothAddress) }
        )
    }
    var showBluetoothPicker by remember { mutableStateOf(false) }

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter
    val pairedDevices = remember {
        bluetoothAdapter?.bondedDevices?.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: "Unknown device",
                address = device.address
            )
        }?.sortedBy { it.name } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (vehicle == null) R.string.add_vehicle else R.string.edit_vehicle
                )
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
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.bluetooth_device),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (showBluetoothPicker) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.height(200.dp)
                        ) {
                            if (pairedDevices.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.no_paired_devices),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                items(pairedDevices) { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedBluetooth = device
                                                showBluetoothPicker = false
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = device.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showBluetoothPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedBluetooth?.name
                                ?: stringResource(R.string.select_bluetooth_device)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedBluetooth?.let { bt ->
                        onSave(VehicleName.resolve(name, bt.name), bt.address, bt.name)
                    }
                },
                enabled = selectedBluetooth != null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun HistoryList(
    locations: List<ParkingLocationEntity>,
    timeFormatter: ParkingTimeFormatter,
    onLocationSelected: (ParkingLocationEntity) -> Unit,
    onDeleteLocation: (ParkingLocationEntity) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge
            )
            if (locations.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = stringResource(R.string.clear_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (locations.isEmpty()) {
            Text(
                text = stringResource(R.string.no_history),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn {
                items(locations) { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLocationSelected(location) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = timeFormatter.format(location.timestamp, System.currentTimeMillis()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = formatCoordinates(location.latitude, location.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteLocation(location) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FloatingLocationCard(
    location: ParkingLocationEntity,
    timeFormatter: ParkingTimeFormatter,
    historyCount: Int,
    isFromHistory: Boolean,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackToLatestClick: () -> Unit,
    onCenterMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.parked_at),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = timeFormatter.format(location.timestamp, System.currentTimeMillis()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isFromHistory) {
                        TextButton(
                            onClick = onBackToLatestClick,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.back_to_latest),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCenterMapClick) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.center_map)
                        )
                    }
                    if (historyCount > 1) {
                        IconButton(onClick = onHistoryClick) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = stringResource(R.string.history_title)
                            )
                        }
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_location)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoLocationContent(
    onSaveLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.no_location_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.no_location_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = onSaveLocationClick) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = stringResource(R.string.save_location_button))
            }
        }
    }
}

private fun formatCoordinates(lat: Double, lon: Double): String {
    return String.format(Locale.US, "%.6f, %.6f", lat, lon)
}

@Composable
private fun PermissionBanner(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permissions_required),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.permissions_required_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGrantClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.grant_permissions))
            }
        }
    }
}
