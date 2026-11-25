package com.dicren.autospot.ui.parking.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.theme.AutoSpotTheme

/**
 * Design-time previews for the stateless components. They are the reason the module keeps
 * `ui-tooling-preview`; the app itself never renders them.
 */
private val previewVehicle =
    VehicleEntity(
        id = 1,
        name = "My car",
        bluetoothAddress = "AA:BB:CC:DD:EE:01",
        bluetoothName = "Car multimedia",
        isActive = true,
    )

private val previewLocation =
    ParkingLocationEntity(
        id = 1,
        vehicleId = 1,
        latitude = 42.2406,
        longitude = -8.7207,
        timestamp = 1_700_000_000_000L,
        street = "Rúa Nova 5",
        locality = "Santiago de Compostela",
        addressResolved = true,
    )

@Preview(showBackground = true)
@Composable
private fun VehicleCardPreview() {
    AutoSpotTheme {
        VehicleCard(vehicle = previewVehicle, onSettingsClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationCardPreview() {
    AutoSpotTheme {
        LocationCard(
            location = previewLocation,
            timeFormatter = ParkingTimeFormatter(LocalResources.current),
            nowMillis = previewLocation.timestamp + 5 * 60_000L,
            historyCount = 3,
            isFromHistory = false,
            canStepOlder = true,
            canStepNewer = false,
            onShareClick = {},
            onOpenHistory = {},
            onBackToLatestClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NoLocationCardPreview() {
    AutoSpotTheme {
        NoLocationCard(isSaving = false, onSaveLocationClick = {})
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PermissionBannerPreview() {
    AutoSpotTheme {
        PermissionBanner(onGrantClick = {}, isApproximateOnly = true)
    }
}
