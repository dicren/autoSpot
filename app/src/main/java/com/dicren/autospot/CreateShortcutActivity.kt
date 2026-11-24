package com.dicren.autospot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dicren.autospot.data.VehicleEntity
import com.dicren.autospot.ui.theme.AutoSpotTheme

class CreateShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AutoSpotTheme {
                VehicleSelectionDialog(
                    onVehicleSelected = { vehicle ->
                        createShortcutForVehicle(vehicle)
                    },
                    onDismiss = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private fun createShortcutForVehicle(vehicle: VehicleEntity) {
        val shortcutIntent =
            Intent(this, NavigateActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(NavigateActivity.EXTRA_VEHICLE_ID, vehicle.id)
            }

        val shortcut =
            ShortcutInfoCompat
                .Builder(this, "navigate_to_${vehicle.id}")
                .setShortLabel(vehicle.name)
                .setLongLabel(getString(R.string.shortcut_long_label_vehicle, vehicle.name))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_navigate))
                .setIntent(shortcutIntent)
                .build()

        val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcut)

        setResult(RESULT_OK, pinnedShortcutCallbackIntent)
        finish()
    }
}

@Composable
private fun VehicleSelectionDialog(
    onVehicleSelected: (VehicleEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as AutoSpotApp }
    val vehicles by app.container.vehicleRepository.vehicles
        .collectAsState(initial = emptyList())

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .clickable(enabled = false, onClick = {}),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.select_vehicle_shortcut),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                if (vehicles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_vehicles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                    ) {
                        items(vehicles) { vehicle ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onVehicleSelected(vehicle) }
                                        .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = vehicle.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = vehicle.bluetoothName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
