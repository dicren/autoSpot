package com.dicren.autospot.ui.parking

import androidx.compose.ui.graphics.Color

/**
 * One colour per vehicle in the map overview.
 *
 * It is derived from the vehicle id instead of its place in the list, so a vehicle keeps its
 * colour when another one is added or removed. The palette is picked to stay distinguishable on
 * top of a light or dark map, and it never carries meaning on its own: the history says which
 * vehicle each spot belongs to.
 */
private val VEHICLE_COLORS =
    listOf(
        Color(0xFFD32F2F),
        Color(0xFF1976D2),
        Color(0xFF388E3C),
        Color(0xFFF57C00),
        Color(0xFF7B1FA2),
        Color(0xFF0097A7),
        Color(0xFF5D4037),
        Color(0xFFC2185B),
    )

/** Stable colour for [vehicleId]; negative ids cannot happen, but the modulo stays in range. */
internal fun vehicleSpotColor(vehicleId: Long): Color {
    val index = ((vehicleId % VEHICLE_COLORS.size) + VEHICLE_COLORS.size) % VEHICLE_COLORS.size
    return VEHICLE_COLORS[index.toInt()]
}
