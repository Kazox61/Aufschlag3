package com.kazox.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/** Which edge of the anchor the popup is placed against. */
internal enum class PopupSide {
    Top,
    Bottom,
    Start,
    End,
}

/** How the popup is aligned along the anchor edge it sits against. */
internal enum class PopupAlign {
    Start,
    Center,
    End,
}

// Positions a popup outside its anchor on the requested [side] with a [gap]
// (in px), aligned along that edge per [align] and the layout direction.
// Flips to the opposite side when the popup would overflow the window but
// fits on the other side, and clamps the result to the window bounds.
internal class AnchoredPopupPositionProvider(
    private val side: PopupSide,
    private val align: PopupAlign = PopupAlign.Start,
    private val gap: Int = 0,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val isLtr = layoutDirection == LayoutDirection.Ltr

        val x: Int
        val y: Int
        when (side) {
            PopupSide.Top, PopupSide.Bottom -> {
                x = alignHorizontally(anchorBounds, width, isLtr)

                val below = anchorBounds.bottom + gap
                val above = anchorBounds.top - gap - height
                val fitsBelow = below + height <= windowSize.height
                val fitsAbove = above >= 0
                y =
                    if (side == PopupSide.Bottom) {
                        if (fitsBelow || !fitsAbove) below else above
                    } else {
                        if (fitsAbove || !fitsBelow) above else below
                    }
            }

            PopupSide.Start, PopupSide.End -> {
                y = alignVertically(anchorBounds, height)

                val after = anchorBounds.right + gap
                val before = anchorBounds.left - gap - width
                val fitsAfter = after + width <= windowSize.width
                val fitsBefore = before >= 0
                // Start/End follow the layout direction: End is to the right in LTR, left in RTL.
                val preferAfter = (side == PopupSide.End) == isLtr
                x =
                    if (preferAfter) {
                        if (fitsAfter || !fitsBefore) after else before
                    } else {
                        if (fitsBefore || !fitsAfter) before else after
                    }
            }
        }

        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - height).coerceAtLeast(0)),
        )
    }

    private fun alignHorizontally(
        anchor: IntRect,
        width: Int,
        isLtr: Boolean,
    ): Int =
        when (align) {
            PopupAlign.Start -> if (isLtr) anchor.left else anchor.right - width
            PopupAlign.Center -> anchor.left + (anchor.width - width) / 2
            PopupAlign.End -> if (isLtr) anchor.right - width else anchor.left
        }

    private fun alignVertically(
        anchor: IntRect,
        height: Int,
    ): Int =
        when (align) {
            PopupAlign.Start -> anchor.top
            PopupAlign.Center -> anchor.top + (anchor.height - height) / 2
            PopupAlign.End -> anchor.bottom - height
        }
}
