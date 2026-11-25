package com.dicren.autospot.ui.parking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.components.TooltippedIconButton
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.format.formatAddress

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
                    // The street name replaces "Parked" once the background lookup finds it; until
                    // then (or when it finds nothing) the generic title stays.
                    Text(
                        text =
                            formatAddress(location.street, location.locality)
                                ?: stringResource(R.string.parked_at),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = timeFormatter.format(location.timestamp, nowMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Kept in the action row, not as a row of its own: a second line made the
                    // card taller every time an old spot was selected.
                    if (isFromHistory) {
                        TooltippedIconButton(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            label = stringResource(R.string.back_to_latest),
                            onClick = onBackToLatestClick,
                        )
                    }
                    if (historyCount > 1) {
                        TooltippedIconButton(
                            imageVector = Icons.Default.History,
                            label = stringResource(R.string.history_title),
                            onClick = onHistoryClick,
                        )
                    }
                    TooltippedIconButton(
                        imageVector = Icons.Default.Share,
                        label = stringResource(R.string.share_location),
                        onClick = onShareClick,
                    )
                }
            }
        }
    }
}
