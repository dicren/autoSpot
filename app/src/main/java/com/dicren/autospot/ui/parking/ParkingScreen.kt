package com.dicren.autospot.ui.parking

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dicren.autospot.R
import com.dicren.autospot.service.BluetoothMonitorService
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.parking.components.HistorySheet
import com.dicren.autospot.ui.parking.components.LocationCard
import com.dicren.autospot.ui.parking.components.LocationServicesBanner
import com.dicren.autospot.ui.parking.components.NoLocationCard
import com.dicren.autospot.ui.parking.components.PermissionBanner
import com.dicren.autospot.ui.parking.components.VehicleCard
import com.dicren.autospot.ui.parking.components.VehiclesSheet
import com.dicren.autospot.util.MapsIntents
import com.dicren.autospot.util.Permissions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch

private const val TAG = "ParkingScreen"

/** Builds the ViewModel from the application container. */
@Composable
fun ParkingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val factory = remember(context) { ParkingViewModel.factory(context.applicationContext) }
    val viewModel: ParkingViewModel = viewModel(factory = factory)

    ParkingScreen(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    viewModel: ParkingViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalResources.current
    val timeFormatter = remember(resources) { ParkingTimeFormatter(resources) }
    val snackbarHostState = remember { SnackbarHostState() }

    val messages =
        ParkingMessages(
            saved = stringResource(R.string.location_saved),
            saveFailed = stringResource(R.string.notification_save_failed_title),
            noVehicle = stringResource(R.string.no_vehicle_configured),
            duplicateVehicle = stringResource(R.string.vehicle_duplicate),
            updateConflict = stringResource(R.string.vehicle_update_conflict),
            mapsNotInstalled = stringResource(R.string.maps_not_found),
            shareTitle = stringResource(R.string.share_location),
        )

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notifications are optional: the services keep running without them. */ }

    var showVehiclesSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val vehiclesSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val historySheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val cameraPositionState = rememberCameraPositionState()

    // Permissions can change while the app is in the background (user went to Settings).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshEnvironment()
        }
    }

    // Start the monitor only when there is something to monitor and every permission is in place.
    // Waiting for `isReady` avoids stopping the monitor the boot receiver just started on the
    // first composition, when every state value is still the default one.
    LaunchedEffect(state.isReady, state.vehicles.isNotEmpty(), state.hasMonitoringPermission) {
        if (!state.isReady) return@LaunchedEffect
        val shouldMonitor = state.vehicles.isNotEmpty() && state.hasMonitoringPermission
        if (shouldMonitor) {
            try {
                context.startForegroundService(
                    Intent(context, BluetoothMonitorService::class.java),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the Bluetooth monitor", e)
            }
        } else {
            context.stopService(Intent(context, BluetoothMonitorService::class.java))
        }
    }

    // The ongoing notification is only visible with POST_NOTIFICATIONS on Android 13+.
    LaunchedEffect(state.vehicles.isNotEmpty(), state.hasMonitoringPermission) {
        if (state.vehicles.isNotEmpty() &&
            state.hasMonitoringPermission &&
            Permissions.needsNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(messages.forEvent(event))
        }
    }

    val displayLocation = state.displayLocation
    LaunchedEffect(displayLocation?.id) {
        displayLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17f),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (displayLocation != null) {
            val position = LatLng(displayLocation.latitude, displayLocation.longitude)

            @SuppressLint("MissingPermission")
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties =
                    MapProperties(
                        // The Maps SDK throws SecurityException if the blue dot is enabled without
                        // the location permission (e.g. after revoking it in Settings).
                        isMyLocationEnabled = state.hasLocationPermission,
                    ),
                uiSettings =
                    MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                    ),
            ) {
                Marker(
                    state = rememberUpdatedMarkerState(position = position),
                    title = stringResource(R.string.marker_title),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    ).padding(16.dp),
        ) {
            if (state.isReady) {
                if (!state.hasMonitoringPermission) {
                    PermissionBanner(
                        onGrantClick = { context.openAppSettings() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!state.isLocationEnabled) {
                    LocationServicesBanner(
                        onSettingsClick = { context.openLocationSettings() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                VehicleCard(
                    vehicle = state.activeVehicle,
                    onSettingsClick = { showVehiclesSheet = true },
                )
            }
        }

        AnimatedVisibility(
            visible = cameraPositionState.position.bearing != 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.End,
                        ),
                    ).padding(top = 92.dp, end = 16.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition
                                    .Builder()
                                    .target(cameraPositionState.position.target)
                                    .zoom(cameraPositionState.position.zoom)
                                    .bearing(0f)
                                    .tilt(0f)
                                    .build(),
                            ),
                        )
                    }
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = stringResource(R.string.reset_orientation),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (displayLocation != null) {
            FloatingActionButton(
                onClick = {
                    val opened =
                        MapsIntents.openNavigation(
                            context,
                            displayLocation.latitude,
                            displayLocation.longitude,
                        )
                    if (!opened) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(messages.mapsNotInstalled)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.End,
                            ),
                        ).padding(end = 16.dp, bottom = 132.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.navigate_button),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }

            LocationCard(
                location = displayLocation,
                timeFormatter = timeFormatter,
                historyCount = state.locations.size,
                isFromHistory = state.isShowingHistory,
                onShareClick = {
                    context.startActivity(
                        MapsIntents.shareIntent(
                            displayLocation.latitude,
                            displayLocation.longitude,
                            messages.shareTitle,
                        ),
                    )
                },
                onHistoryClick = { showHistorySheet = true },
                onBackToLatestClick = viewModel::showLatestLocation,
                onCenterMapClick = {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(displayLocation.latitude, displayLocation.longitude),
                                17f,
                            ),
                        )
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                            ),
                        ).padding(16.dp),
            )
        } else if (state.isReady) {
            NoLocationCard(
                isSaving = state.isSaving,
                onSaveLocationClick = viewModel::saveCurrentLocation,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    ).padding(start = 16.dp, end = 16.dp, bottom = 120.dp),
        )
    }

    if (showVehiclesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVehiclesSheet = false },
            sheetState = vehiclesSheetState,
        ) {
            VehiclesSheet(
                vehicles = state.vehicles,
                activeVehicle = state.activeVehicle,
                canReadBluetooth = Permissions.hasBluetoothConnect(context),
                onVehicleClick = viewModel::activateVehicle,
                onAddVehicle = viewModel::addVehicle,
                onEditVehicle = viewModel::updateVehicle,
                onDeleteVehicle = viewModel::deleteVehicle,
            )
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = historySheetState,
        ) {
            HistorySheet(
                locations = state.locations,
                timeFormatter = timeFormatter,
                onLocationSelected = { location ->
                    viewModel.selectLocation(location)
                    showHistorySheet = false
                },
                onDeleteLocation = viewModel::deleteLocation,
                onRequestClearAll = { confirmClearHistory = true },
            )
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
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
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClearHistory = false
                        showHistorySheet = false
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private data class ParkingMessages(
    val saved: String,
    val saveFailed: String,
    val noVehicle: String,
    val duplicateVehicle: String,
    val updateConflict: String,
    val mapsNotInstalled: String,
    val shareTitle: String,
) {
    fun forEvent(event: ParkingEvent): String =
        when (event) {
            ParkingEvent.LocationSaved -> saved
            ParkingEvent.SaveFailed -> saveFailed
            ParkingEvent.NoVehicle -> noVehicle
            ParkingEvent.DuplicateVehicle -> duplicateVehicle
            ParkingEvent.VehicleUpdateConflict -> updateConflict
        }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

private fun Context.openLocationSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.location_services_disabled, Toast.LENGTH_LONG).show()
    }
}
