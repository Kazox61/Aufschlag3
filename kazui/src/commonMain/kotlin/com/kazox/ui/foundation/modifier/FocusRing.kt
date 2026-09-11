package com.kazox.ui.foundation.modifier

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazox.ui.foundation.KazTheme

/**
 * Applies a focus ring border using the theme's [ring][com.kazox.ui.foundation.KazColors.ring] color.
 *
 * This is the KazUI equivalent of shadcn's `focus-visible:ring-2`.
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
 * @param shape The shape of the ring border (should match the component shape).
 * @param width The ring border width. Defaults to 2dp.
 * @param offset Additional offset from the component edge. Defaults to 2dp.
 */
@Composable
public fun Modifier.focusRing(
    shape: Shape,
    width: Dp = 2.dp,
    offset: Dp = 2.dp,
): Modifier {
    val ringColor = KazTheme.colors.ring
    return this then Modifier.border(width = width + offset, color = ringColor, shape = shape)
}
