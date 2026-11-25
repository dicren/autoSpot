package com.dicren.autospot.ui.parking.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import com.dicren.autospot.data.ParkingLocationEntity
import com.dicren.autospot.ui.components.TooltippedIconButton
import com.dicren.autospot.ui.format.ParkingTimeFormatter
import com.dicren.autospot.ui.format.formatAddress

/** Test tag for the card itself, so layout tests measure it instead of guessing its padding. */
internal const val LOCATION_CARD_TAG = "location_card"

/** Test tag for the grabber that advertises the card's gestures. */
internal const val HISTORY_HANDLE_TAG = "location_card_history_handle"

/**
 * The spot on screen, with its share action and, for an old spot, the way back to the latest one.
 *
 * The card is also the handle of the parking history: swiping it up opens the history sheet and
 * swiping it sideways walks that history. The old history button is gone; [HistoryHandle] is the
 * visual cue for both gestures, and it is also what accessibility services activate.
 *
 * The way back is the rightmost action, after Share: it is the edge the card already reads as
 * "newest", and the glyph points that way. Share keeps the edge while there is nothing to go back
 * to, because the arrow is only composed with a history selection.
 */
@Composable
fun LocationCard(
    location: ParkingLocationEntity,
    timeFormatter: ParkingTimeFormatter,
    nowMillis: Long,
    historyCount: Int,
    isFromHistory: Boolean,
    canStepOlder: Boolean,
    canStepNewer: Boolean,
    onShareClick: () -> Unit,
    onOpenHistory: () -> Unit,
    onBackToLatestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(LOCATION_CARD_TAG),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = parkingCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // With a single spot there is no history to open and nothing to page through, so the
            // grabber would only promise gestures that do nothing.
            if (historyCount > 1) {
                HistoryHandle(
                    canStepOlder = canStepOlder,
                    canStepNewer = canStepNewer,
                    onOpenHistory = onOpenHistory,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TooltippedIconButton(
                        imageVector = Icons.Default.Share,
                        label = stringResource(R.string.share_location),
                        onClick = onShareClick,
                    )
                    // The way back is the last thing on the right, and it is a pair with the glyph:
                    // an arrow that points right only reads as "the newest" on that edge, which is
                    // the one the card already uses for "newer" (see the chevron above). Share
                    // gives up the edge while it is there and slides back when it leaves, instead
                    // of the icon jumping to a new place; the spacing lives inside the animation so
                    // nothing is left behind when it collapses.
                    AnimatedVisibility(
                        visible = isFromHistory,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(ACTION_SPACING))
                            TooltippedIconButton(
                                imageVector = Icons.Default.KeyboardDoubleArrowRight,
                                label = stringResource(R.string.back_to_latest),
                                onClick = onBackToLatestClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The grabber at the top of the card: a pill in the middle and a chevron on each side.
 *
 * The pill is the standard "drag me" mark of a sheet, and the only direction this card can move is
 * up, which is what the history sheet follows. The chevrons say the card also pages sideways; they
 * dim at the ends of the history instead of disappearing so the card keeps its symmetry and the
 * pair keeps reading as "there are pages in both directions" rather than as two loose arrows.
 *
 * It has no pointer handler — the whole card is the gesture — but it does expose a semantics
 * "click": that is what TalkBack and switch access focus, and it keeps the history sheet reachable
 * now that the old button is gone. A semantics click is not a touch target, so a normal tap on the
 * grabber still does nothing.
 */
@Composable
private fun HistoryHandle(
    canStepOlder: Boolean,
    canStepNewer: Boolean,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(R.string.history_title)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(16.dp)
                .testTag(HISTORY_HANDLE_TAG)
                .semantics {
                    contentDescription = label
                    onClick(label = label) {
                        onOpenHistory()
                        true
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepChevron(
            imageVector = Icons.Default.ChevronLeft,
            enabled = canStepOlder,
            tint = tint,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(color = tint.copy(alpha = HANDLE_ALPHA), shape = CircleShape),
        )
        Spacer(modifier = Modifier.weight(1f))
        StepChevron(
            imageVector = Icons.Default.ChevronRight,
            enabled = canStepNewer,
            tint = tint,
        )
    }
}

@Composable
private fun StepChevron(
    imageVector: ImageVector,
    enabled: Boolean,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint.copy(alpha = if (enabled) CHEVRON_ENABLED_ALPHA else CHEVRON_DISABLED_ALPHA),
        modifier = Modifier.size(16.dp),
    )
}

/** Quiet enough to sit above the address without competing with it. */
private const val HANDLE_ALPHA = 0.35f

/**
 * Gap between the two card actions. It is part of the arrow's animation, not a `spacedBy` on the
 * row, so Share sits flush with the edge whenever the arrow is gone.
 */
private val ACTION_SPACING = 4.dp

/** Readable as "there is another page" without looking like a button. */
private const val CHEVRON_ENABLED_ALPHA = 0.6f

/** Still visible, so the card does not look lopsided at the ends of the history. */
private const val CHEVRON_DISABLED_ALPHA = 0.25f
