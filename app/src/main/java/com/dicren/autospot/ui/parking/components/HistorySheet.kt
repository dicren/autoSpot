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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.format.formatAddress
import java.util.Locale

@Composable
fun HistorySheet(
    locations: List<ParkingLocationEntity>,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    onLocationSelected: (ParkingLocationEntity) -> Unit,
    onDeleteLocation: (ParkingLocationEntity) -> Unit,
    onRequestClearAll: () -> Unit,
    modifier: Modifier = Modifier,
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
            )
            if (locations.isNotEmpty()) {
                TextButton(onClick = onRequestClearAll) {
                    Text(
                        text = stringResource(R.string.clear_all),
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
                                    formatAddress(location.street, location.locality)
                                        ?: formatCoordinates(location.latitude, location.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteLocation(location) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
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

private fun formatCoordinates(
    lat: Double,
    lon: Double,
): String = String.format(Locale.US, "%.6f, %.6f", lat, lon)
