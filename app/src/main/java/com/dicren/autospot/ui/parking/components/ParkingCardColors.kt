package com.dicren.autospot.ui.parking.components

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Colors for the cards that float over the map.
 *
 * The container keeps the surface tone with a little transparency so the map shows through. That
 * translucent color is not one of the scheme colors, so [CardDefaults.cardColors] cannot resolve a
 * matching content color through `contentColorFor` and leaves it unspecified: the texts and icons
 * inside the card then fall back to black, which is unreadable over the dark surface of the dark
 * theme (it hid the whole action row of the location card). State the content color explicitly.
 */
@Composable
internal fun parkingCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
