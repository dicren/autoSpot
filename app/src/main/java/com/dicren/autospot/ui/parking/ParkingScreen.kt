package com.dicren.autospot.ui.parking

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
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
    val locationUnavailable = stringResource(R.string.location_unavailable)
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
    // Not saveable on purpose: a rotation re-reads it from the provider on the next resume, and a
    // stale point is worse than a brief "unknown".
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
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
    ParkingMyLocationEffect(
        lifecycleOwner = lifecycleOwner,
        hasLocationPermission = state.hasLocationPermission,
        findMyLocation = viewModel::findMyLocation,
        onLocation = { fix -> myLocation = LatLng(fix.latitude, fix.longitude) },
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
    ParkingCameraEffect(
        displayLocation = displayLocation,
        isOverviewing = state.isOverviewing,
        cameraPositionState = cameraPositionState,
    )
    ParkingOverviewCameraEffect(state = state, cameraPositionState = cameraPositionState)

    // Room left around the spots a cluster frames, so its markers are not cut by the screen edges.
    val overviewPaddingPx = with(LocalDensity.current) { OVERVIEW_PADDING_DP.dp.roundToPx() }

    /** Shows [ids] on the card and frames them; used by a marker tap and by a cluster tap. */
    fun focusSpots(ids: Set<Long>) {
        viewModel.focusCluster(ids)
        val spots = state.overviewLocations.filter { it.id in ids }
        cameraUpdateFor(spots, overviewPaddingPx)?.let { update ->
            coroutineScope.launch { cameraPositionState.animate(update) }
        }
    }

    // Back leaves the overview instead of the app: the mode is a place the user went to.
    BackHandler(enabled = state.isOverviewing) { viewModel.closeOverview() }

    Box(modifier = modifier.fillMaxSize()) {
        // The overview needs the map even with no card on screen, since the spots are the point.
        if (displayLocation != null || (state.isOverviewing && state.overviewLocations.isNotEmpty())) {
            ParkingMap(
                location = displayLocation,
                overviewSpots = if (state.isOverviewing) state.overviewLocations else null,
                latestSpotIds = state.latestSpotIds,
                isMyLocationEnabled = state.hasLocationPermission,
                cameraPositionState = cameraPositionState,
                onSpotClick = { id -> focusSpots(setOf(id)) },
                onClusterClick = { ids -> focusSpots(ids) },
                // An empty tap on the map drops the card and goes back to every spot.
                onMapClick = { if (state.isClusterFocused) viewModel.clearClusterFocus() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        ParkingTopOverlay(
            state = state,
            showBackgroundRationale = showBackgroundRationale,
            cameraPositionState = cameraPositionState,
            myLocation = myLocation,
            onGrantClick = { context.openAppSettings() },
            onLocationSettingsClick = { context.openLocationSettings() },
            onVehiclesClick = { showVehiclesSheet = true },
            onMyLocationClick = {
                coroutineScope.launch {
                    val fix = viewModel.findMyLocation()
                    if (fix != null) {
                        val position = LatLng(fix.latitude, fix.longitude)
                        // Remembered so the button can hide itself once the camera gets there.
                        myLocation = position
                        // Only the centre moves: the zoom the user chose is kept.
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(position))
                    } else {
                        snackbarHostState.showSnackbar(locationUnavailable)
                    }
                }
            },
            onResetBearing = {
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
            onExitOverview = viewModel::closeOverview,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (displayLocation == null && !state.isOverviewing && state.isReady) {
            NoLocationCard(
                isSaving = state.isSaving,
                onSaveLocationClick = viewModel::saveCurrentLocation,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        ParkingBottomControls(
            snackbarHostState = snackbarHostState,
            location = displayLocation,
            cameraPositionState = cameraPositionState,
            timeFormatter = timeFormatter,
            nowMillis = nowMillis,
            historyCount = state.historyLocations.size,
            isShowingHistory = state.isShowingHistory,
            isHistorySheetOpen = showHistorySheet,
            canShowOlder = state.canStepOlder,
            canShowNewer = state.canStepNewer,
            isOverviewing = state.isOverviewing,
            onShowAllSpots = {
                // "Show all spots" is the way back to the scope: keeping a group's card on screen
                // would leave one spot in front of the view it is meant to restore.
                viewModel.clearClusterFocus()
                cameraUpdateFor(state.overviewLocations, overviewPaddingPx)?.let { update ->
                    coroutineScope.launch { cameraPositionState.animate(update) }
                }
            },
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
                    onRecenter = { location ->
                        coroutineScope.launch {
                            // Only the centre moves: the zoom the user chose is kept.
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLng(
                                    LatLng(location.latitude, location.longitude),
                                ),
                            )
                        }
                    },
                    onHistory = { showHistorySheet = true },
                    onBackToLatest = viewModel::showLatestLocation,
                    // Swiping the card walks the history; both directions stop at the ends.
                    onOlder = { state.stepIndex(older = true)?.let { viewModel.showSpotAt(state, it) } },
                    onNewer = { state.stepIndex(older = false)?.let { viewModel.showSpotAt(state, it) } },
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
                onClearCluster = {
                    viewModel.clearClusterHistory()
                    confirmClearHistory = false
                    // The list would otherwise swap from the group to the whole scope under the
                    // user, which is not what "delete this group" means.
                    showHistorySheet = false
                },
                onShowVehicleOnMap = {
                    viewModel.showOverviewOnMap(MapScope.ACTIVE_VEHICLE)
                    showHistorySheet = false
                },
                onShowAllVehiclesOnMap = {
                    viewModel.showOverviewOnMap(MapScope.ALL_VEHICLES)
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

/**
 * Shows the spot at [index] of the newest-first history. Index 0 is the latest spot, which is
 * represented by clearing the selection: selecting it explicitly would keep the "back to latest"
 * arrow on screen while the card is already showing the newest spot.
 *
 * It indexes [ParkingUiState.historyLocations], not the raw list: in the overview the history is
 * the group the user picked.
 */
private fun ParkingViewModel.showSpotAt(
    state: ParkingUiState,
    index: Int,
) {
    if (index == 0) showLatestLocation() else selectLocation(state.historyLocations[index])
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
