package com.dicren.autospot.ui.parking.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.components.TooltippedIconButton
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.format.formatAddress
import java.util.Locale

/**
 * The parking history, either the whole of a scope or only the group of spots the map is showing.
 *
 * The header carries the way out to the map: one vehicle at a time, and — only when there is more
 * than one — every vehicle at once. Both need at least one spot, because there would be nothing
 * to draw.
 */
@Composable
fun HistorySheet(
    locations: List<ParkingLocationEntity>,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    onLocationSelected: (ParkingLocationEntity) -> Unit,
    onDeleteLocation: (ParkingLocationEntity) -> Unit,
    onRequestClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** Vehicle names by id; only read when [showVehicleNames] is true. */
    vehicleNames: Map<Long, String> = emptyMap(),
    /** True while the list mixes vehicles, so each row says which one its spot belongs to. */
    showVehicleNames: Boolean = false,
    /** True when the list holds a map cluster instead of a vehicle's history. */
    isClusterScoped: Boolean = false,
    /** Null hides the button (nothing to show, or the screen has no map). */
    onShowOnMap: (() -> Unit)? = null,
    /** Non-null only when there is more than one vehicle to show. */
    onShowAllVehiclesOnMap: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (locations.isNotEmpty()) {
                // One icon per scope: the map glyph means "this vehicle", the globe "all of them".
                onShowOnMap?.let {
                    TooltippedIconButton(
                        imageVector = Icons.Default.Map,
                        label = stringResource(R.string.view_vehicle_on_map),
                        onClick = it,
                    )
                }
                onShowAllVehiclesOnMap?.let {
                    TooltippedIconButton(
                        imageVector = Icons.Default.Public,
                        label = stringResource(R.string.view_all_on_map),
                        onClick = it,
                    )
                }
                TextButton(onClick = onRequestClearAll) {
                    Text(
                        text =
                            stringResource(
                                if (isClusterScoped) R.string.clear_group else R.string.clear_all,
                            ),
                        color = MaterialTheme.colorScheme.error,
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn {
                items(locations, key = { it.id }) { location ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onLocationSelected(location) }
                                .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = timeFormatter.format(location.timestamp, nowMillis),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    detailLine(
                                        location = location,
                                        vehicleNames = vehicleNames,
                                        showVehicleName = showVehicleNames,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TooltippedIconButton(
                            imageVector = Icons.Default.Delete,
                            label = stringResource(R.string.delete),
                            onClick = { onDeleteLocation(location) },
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * The second line of a row: the address when it is known, the coordinates otherwise, and the
 * vehicle in front of it while the list mixes vehicles.
 */
private fun detailLine(
    location: ParkingLocationEntity,
    vehicleNames: Map<Long, String>,
    showVehicleName: Boolean,
): String {
    val where =
        formatAddress(location.street, location.locality)
            ?: formatCoordinates(location.latitude, location.longitude)
    return if (showVehicleName) {
        listOfNotNull(vehicleNames[location.vehicleId], where).joinToString(" · ")
    } else {
        where
    }
}

private fun formatCoordinates(
    lat: Double,
    lon: Double,
): String = String.format(Locale.US, "%.6f, %.6f", lat, lon)
