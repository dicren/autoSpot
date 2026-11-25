package com.dicren.autospot.ui.format

/**
 * Joins the stored address components into the single line the UI shows: "Calle Mayor 12, Getafe".
 *
 * Returns null when there is nothing usable, so callers can fall back to their previous text
 * ("Parked" on the card, the coordinates in the history) instead of rendering an empty string.
 */
fun formatAddress(
    street: String?,
    locality: String?,
): String? =
    listOfNotNull(street, locality)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
        .ifEmpty { null }
