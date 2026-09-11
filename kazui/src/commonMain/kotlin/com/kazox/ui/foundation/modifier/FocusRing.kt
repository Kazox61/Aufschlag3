package com.kazox.ui.foundation.modifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazox.ui.foundation.KazTheme

/**
 * Draws a focus ring around the component using the theme's
 * [ring][com.kazox.ui.foundation.KazColors.ring] color.
 *
 * This is the KazUI equivalent of shadcn's `focus-visible:ring-2 ring-offset-2`.
 * The ring is drawn outside the component bounds, separated from the edge by
 * [offset], so it does not affect layout or the component's own border.
 * Apply conditionally when the component is focused:
 *
 * ```
 * val isFocused by interactionSource.collectIsFocusedAsState()
 *
 * Box(
 *     modifier = Modifier
 *         .then(if (isFocused) Modifier.focusRing(shape) else Modifier)
 *         .background(colors.primary, shape),
 * )
 * ```
 *
 * @param shape The shape of the ring (should match the component shape).
 * @param width The ring stroke width. Defaults to 2dp.
 * @param offset Gap between the component edge and the ring. Defaults to 2dp.
 */
@Composable
public fun Modifier.focusRing(
    shape: Shape,
    width: Dp = 2.dp,
    offset: Dp = 2.dp,
): Modifier {
    val ringColor = KazTheme.colors.ring
    return this then
        Modifier.drawBehind {
            val strokePx = width.toPx()
            // Distance from the component edge to the stroke's center line.
            val insetPx = offset.toPx() + strokePx / 2
            val ringSize = Size(size.width + insetPx * 2, size.height + insetPx * 2)
            val stroke = Stroke(width = strokePx)

            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                // Grow the corner radius with the inset so the ring stays concentric.
                is Outline.Rounded -> {
                    val cr = outline.roundRect.topLeftCornerRadius.x + insetPx
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(-insetPx, -insetPx),
                        size = ringSize,
                        cornerRadius = CornerRadius(cr, cr),
                        style = stroke,
                    )
                }

                else -> {
                    translate(left = -insetPx, top = -insetPx) {
                        drawOutline(
                            outline = shape.createOutline(ringSize, layoutDirection, this),
                            color = ringColor,
                            style = stroke,
                        )
                    }
                }
            }
        }
}
