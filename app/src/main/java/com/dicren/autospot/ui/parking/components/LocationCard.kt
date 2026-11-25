package com.dicren.autospot.ui.parking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.format.ParkingTimeFormatter

@Composable
fun LocationCard(
    location: ParkingLocationEntity,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    historyCount: Int,
    isFromHistory: Boolean,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackToLatestClick: () -> Unit,
    onCenterMapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = parkingCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.parked_at),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = timeFormatter.format(location.timestamp, nowMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isFromHistory) {
                        TextButton(onClick = onBackToLatestClick) {
                            Text(
                                text = stringResource(R.string.back_to_latest),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCenterMapClick) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.center_map),
                        )
                    }
                    if (historyCount > 1) {
                        IconButton(onClick = onHistoryClick) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = stringResource(R.string.history_title),
                            )
                        }
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_location),
                        )
                    }
                }
            }
        }
    }
}
