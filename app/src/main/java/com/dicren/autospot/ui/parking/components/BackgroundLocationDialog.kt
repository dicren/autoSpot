package com.dicren.autospot.ui.parking.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dicren.autospot.R

/**
 * Explains why Android needs "Allow all the time" before the app sends the user to the system
 * settings page. The callbacks own the decision to record that the explanation was shown, so a
 * dialog that never reaches the user is not persisted as seen.
 */
@Composable
fun BackgroundLocationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.background_location_title)) },
        text = { Text(stringResource(R.string.background_location_description)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.continue_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}
