package com.dicren.autospot.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/** Distance between the button and its tooltip. */
private val TOOLTIP_GAP = 8.dp

/** Distance the tooltip keeps from the edge of the safe area. */
private val TOOLTIP_EDGE_MARGIN = 8.dp

/**
 * The explanation of an icon-only control, revealed by a long press (and by hovering, where there
 * is a pointer).
 *
 * An icon is the only label those controls have, and the glyph is always a guess: a crosshair can
 * mean "my location" or "the car", a car can mean "go there" or "navigate there". The tooltip is
 * the cheap way to answer it without opening anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()
    val gapPx = with(density) { TOOLTIP_GAP.roundToPx() }
    val marginPx = with(density) { TOOLTIP_EDGE_MARGIN.roundToPx() }
    val startPx = with(density) { safeArea.calculateStartPadding(layoutDirection).roundToPx() }
    val endPx = with(density) { safeArea.calculateEndPadding(layoutDirection).roundToPx() }
    val topPx = with(density) { safeArea.calculateTopPadding().roundToPx() }
    val bottomPx = with(density) { safeArea.calculateBottomPadding().roundToPx() }
    val positionProvider =
        remember(gapPx, marginPx, startPx, endPx, topPx, bottomPx) {
            EdgeSafeTooltipPositionProvider(
                gapPx = gapPx,
                marginPx = marginPx,
                startInsetPx = startPx,
                endInsetPx = endPx,
                topInsetPx = topPx,
                bottomInsetPx = bottomPx,
            )
        }

    TooltipBox(
        positionProvider = positionProvider,
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * Places the tooltip above its button, or below it when there is no room above, and then moves it
 * back inside the safe area on both axes.
 *
 * Material's own provider works from the whole window, which is not enough here: the buttons sit
 * on the screen edges (the map FABs, the arrows of the location card, the sheet actions) and the
 * app draws edge to edge, so a tooltip centred on a button near the right edge was half outside
 * and came out cut. The caret may point at nothing when the tooltip has to flip below, which is
 * the lesser evil: a tooltip you cannot read is worse than one whose tail is on the wrong side.
 */
internal class EdgeSafeTooltipPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val startInsetPx: Int = 0,
    private val endInsetPx: Int = 0,
    private val topInsetPx: Int = 0,
    private val bottomInsetPx: Int = 0,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left = startInsetPx + marginPx
        val right = windowSize.width - endInsetPx - marginPx
        val top = topInsetPx + marginPx
        val bottom = windowSize.height - bottomInsetPx - marginPx

        val above = anchorBounds.top - gapPx - popupContentSize.height
        val below = anchorBounds.bottom + gapPx
        val y = if (above >= top) above else below
        val centredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2

        return IntOffset(
            x = centredX.coerceIn(left, maxOf(left, right - popupContentSize.width)),
            y = y.coerceIn(top, maxOf(top, bottom - popupContentSize.height)),
        )
    }
}

/**
 * An icon-only button that always carries a tooltip. [label] is used both as the accessibility
 * description and as the tooltip text, so the two cannot drift apart.
 */
@Composable
fun TooltippedIconButton(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    IconTooltip(text = label) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                tint = tint,
            )
        }
    }
}
