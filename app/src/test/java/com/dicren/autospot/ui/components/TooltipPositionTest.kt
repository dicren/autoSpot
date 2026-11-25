package com.dicren.autospot.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The buttons sit on the edges of the screen (map FABs, card arrows, sheet actions), which is where
 * a tooltip anchored naively ends up half outside and comes out cut.
 */
class TooltipPositionTest {
    private val window = IntSize(width = 1080, height = 2400)
    private val tooltip = IntSize(width = 400, height = 100)
    private val gap = 16
    private val margin = 8
    private val provider = EdgeSafeTooltipPositionProvider(gapPx = gap, marginPx = margin)

    private fun positionOf(anchor: IntRect): IntOffset =
        provider.calculatePosition(anchor, window, LayoutDirection.Ltr, tooltip)

    @Test
    fun `a button with room above gets its tooltip on top of it`() {
        val anchor = IntRect(left = 540, top = 1200, right = 636, bottom = 1296)

        val position = positionOf(anchor)

        assertEquals(1200 - gap - tooltip.height, position.y)
        assertEquals(540 + (anchor.width - tooltip.width) / 2, position.x)
    }

    @Test
    fun `a button on the top edge gets its tooltip below`() {
        val anchor = IntRect(left = 500, top = 40, right = 596, bottom = 136)

        val position = positionOf(anchor)

        assertEquals(136 + gap, position.y)
    }

    @Test
    fun `a button on the right edge pulls the tooltip back inside`() {
        val anchor = IntRect(left = 980, top = 1200, right = 1076, bottom = 1296)

        val position = positionOf(anchor)

        assertEquals(window.width - margin - tooltip.width, position.x)
    }

    @Test
    fun `a button on the left edge pushes the tooltip inside`() {
        val anchor = IntRect(left = 4, top = 1200, right = 100, bottom = 1296)

        val position = positionOf(anchor)

        assertEquals(margin, position.x)
    }

    @Test
    fun `the safe area insets are respected`() {
        val insets =
            EdgeSafeTooltipPositionProvider(
                gapPx = 0,
                marginPx = 0,
                startInsetPx = 20,
                endInsetPx = 40,
                topInsetPx = 100,
                bottomInsetPx = 50,
            )
        val anchor = IntRect(left = 1000, top = 120, right = 1096, bottom = 216)

        val position = insets.calculatePosition(anchor, window, LayoutDirection.Ltr, tooltip)

        // Below the button, but never on top of the status bar.
        assertEquals(216, position.y)
        assertEquals(window.width - 40 - tooltip.width, position.x)
    }

    @Test
    fun `a tooltip taller than the window still starts at the margin`() {
        val anchor = IntRect(left = 500, top = 40, right = 596, bottom = 136)

        val position =
            provider.calculatePosition(
                anchor,
                window,
                LayoutDirection.Ltr,
                IntSize(width = 400, height = 3000),
            )

        assertEquals(margin, position.y)
    }
}
