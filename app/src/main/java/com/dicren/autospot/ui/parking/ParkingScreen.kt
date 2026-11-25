package com.dicren.autospot.ui.parking

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dicren.autospot.R
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.parking.components.BackgroundLocationDialog
import com.dicren.autospot.ui.parking.components.NoLocationCard
import com.dicren.autospot.util.BluetoothDeviceInfo
import com.dicren.autospot.util.BluetoothDevices
import com.dicren.autospot.util.MapsIntents
import com.dicren.autospot.util.Permissions
import com.dicren.autospot.util.openAppSettings
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ParkingScreen"

/** How often the relative "parked at" text is refreshed while the screen stays composed. */
private const val NOW_TICK_MILLIS = 60_000L

/** Builds the ViewModel from the application container. */
@Composable
fun ParkingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val factory = remember(context) { ParkingViewModel.factory(context.applicationContext) }
    val viewModel: ParkingViewModel = viewModel(factory = factory)

    ParkingScreen(viewModel = viewModel, modifier = modifier)
}

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
            permissionMissing = stringResource(R.string.location_permission_missing),
            duplicateVehicle = stringResource(R.string.vehicle_duplicate),
            updateConflict = stringResource(R.string.vehicle_update_conflict),
            operationFailed = stringResource(R.string.operation_failed),
        )
    // The event collector below outlives configuration changes, so it must read the latest
    // translated strings instead of the ones captured on its first composition.
    val currentMessages by rememberUpdatedState(messages)
    val monitorFailedMessage = stringResource(R.string.monitor_failed_text)
    val mapsNotInstalled = stringResource(R.string.maps_not_found)
    val shareTitle = stringResource(R.string.share_location)

    // Android 11+ answers this request by sending the user to the system's location page; the
    // flags are refreshed on resume, and immediately here for the versions that answer inline.
    val backgroundLocationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { viewModel.refreshEnvironment() }

    var showVehiclesSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    var showBackgroundRationale by rememberSaveable { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()
    val nowMillis = rememberNow()
    val canReadBluetooth = Permissions.hasBluetoothConnect(context)
    // Reading bonded devices is a binder call: keep it off the main thread and only while the
    // sheet is actually open.
    var devices by remember { mutableStateOf(emptyList<BluetoothDeviceInfo>()) }
    LaunchedEffect(showVehiclesSheet, canReadBluetooth) {
        devices =
            if (showVehiclesSheet) {
                withContext(Dispatchers.IO) { BluetoothDevices.paired(context, canReadBluetooth) }
            } else {
                emptyList()
            }
    }

    ParkingEnvironmentEffect(viewModel = viewModel, lifecycleOwner = lifecycleOwner)
    ParkingMonitorEffect(
        state = state,
        context = context,
        onStartFailed = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(monitorFailedMessage)
            }
        },
    )
    ParkingBackgroundLocationEffect(
        state = state,
        viewModel = viewModel,
        onShowRationale = { showBackgroundRationale = true },
        onHideRationale = { showBackgroundRationale = false },
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(currentMessages.forEvent(event))
        }
    }

    val displayLocation = state.displayLocation
    ParkingCameraEffect(displayLocation = displayLocation, cameraPositionState = cameraPositionState)

    Box(modifier = modifier.fillMaxSize()) {
        if (displayLocation != null) {
            ParkingMap(
                location = displayLocation,
                isMyLocationEnabled = state.hasLocationPermission,
                cameraPositionState = cameraPositionState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ParkingTopOverlay(
            state = state,
            showBackgroundRationale = showBackgroundRationale,
            onGrantClick = { context.openAppSettings() },
            onLocationSettingsClick = { context.openLocationSettings() },
            onVehiclesClick = { showVehiclesSheet = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CompassFab(
            cameraPositionState = cameraPositionState,
            onReset = {
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
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.End,
                        ),
                    ).padding(top = 92.dp, end = 16.dp),
        )

        if (displayLocation == null && state.isReady) {
            NoLocationCard(
                isSaving = state.isSaving,
                onSaveLocationClick = viewModel::saveCurrentLocation,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        ParkingBottomControls(
            snackbarHostState = snackbarHostState,
            location = displayLocation,
            timeFormatter = timeFormatter,
            nowMillis = nowMillis,
            historyCount = state.locations.size,
            isShowingHistory = state.isShowingHistory,
            callbacks =
                ParkingBottomCallbacks(
                    onNavigate = { location ->
                        val opened =
                            MapsIntents.openNavigation(
                                context,
                                location.latitude,
                                location.longitude,
                            )
                        if (!opened) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(mapsNotInstalled)
                            }
                        }
                    },
                    onShare = { location ->
                        runCatching {
                            context.startActivity(
                                MapsIntents.shareIntent(
                                    location.latitude,
                                    location.longitude,
                                    shareTitle,
                                ),
                            )
                        }.onFailure { Log.w(TAG, "No app can share the location", it) }
                    },
                    onCenterMap = { location ->
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(location.latitude, location.longitude),
                                    17f,
                                ),
                            )
                        }
                    },
                    onHistory = { showHistorySheet = true },
                    onBackToLatest = viewModel::showLatestLocation,
                ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    ParkingSheets(
        state = state,
        timeFormatter = timeFormatter,
        nowMillis = nowMillis,
        canReadBluetooth = canReadBluetooth,
        devices = devices,
        showVehicles = showVehiclesSheet,
        showHistory = showHistorySheet,
        confirmClearHistory = confirmClearHistory,
        callbacks =
            ParkingSheetCallbacks(
                onDismissVehicles = { showVehiclesSheet = false },
                onDismissHistory = { showHistorySheet = false },
                onCancelClearHistory = { confirmClearHistory = false },
                onSelectLocation = { location ->
                    viewModel.selectLocation(location)
                    showHistorySheet = false
                },
                onDeleteLocation = viewModel::deleteLocation,
                onRequestClearHistory = { confirmClearHistory = true },
                onClearHistory = {
                    // Pass the vehicle being shown, not whichever vehicle is active when the
                    // coroutine eventually runs.
                    viewModel.clearHistory(state.activeVehicle?.id)
                    confirmClearHistory = false
                    showHistorySheet = false
                },
                onActivateVehicle = viewModel::activateVehicle,
                onAddVehicle = viewModel::addVehicle,
                onEditVehicle = viewModel::updateVehicle,
                onDeleteVehicle = viewModel::deleteVehicle,
            ),
    )

    if (showBackgroundRationale) {
        BackgroundLocationDialog(
            onConfirm = {
                showBackgroundRationale = false
                viewModel.markBackgroundRationaleShown()
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            onDismiss = {
                showBackgroundRationale = false
                viewModel.markBackgroundRationaleShown()
            },
        )
    }
}

/**
 * A wall clock that advances once a minute. Reading the clock inside composition would freeze
 * "5 minutes ago" until some unrelated state changed, which is exactly what the card showed.
 */
@Composable
private fun rememberNow(): Long {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(NOW_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    return now
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
