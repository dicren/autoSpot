package com.dicren.autospot.ui.parking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.ui.components.TooltippedIconButton

@Composable
fun VehicleCard(
    vehicle: VehicleEntity?,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = parkingCardColors(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = vehicle?.name ?: stringResource(R.string.no_vehicle_configured),
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (vehicle != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            TooltippedIconButton(
                imageVector = Icons.Default.Settings,
                label = stringResource(R.string.vehicles_title),
                onClick = onSettingsClick,
            )
        }
    }
}

/**
 * Stands in for [VehicleCard] while the overview shows every vehicle: the screen is not looking at
 * any particular car, so naming one would be a lie. It deliberately has no actions — the vehicles
 * sheet is reached from the normal card, and opening it from here would fight the mode.
 */
@Composable
fun AllVehiclesCard(
    spotCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = parkingCardColors(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.all_vehicles),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.saved_spots, spotCount, spotCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
