package com.kazox.ui.components.popover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import com.kazox.ui.components.AnchoredPopupPositionProvider
import com.kazox.ui.components.PopupAlign
import com.kazox.ui.components.PopupSide
import com.kazox.ui.foundation.LocalContentColor
import com.kazox.ui.foundation.LocalTextStyle
import com.kazox.ui.foundation.KazTheme
import com.kazox.ui.foundation.modifier.LocalMinTouchTarget

// ─── Animation ─────────────────────────────────────────────

public enum class PopoverAnimation {
    /** Fade + vertical expand (default). */
    FadeExpand,

    /** Opacity fade only. */
    Fade,

    /** No animation. */
    None,
}

// ─── Alignment ─────────────────────────────────────────────

public enum class PopoverPlacement {
    BottomStart,
    BottomEnd,
    TopStart,
    TopEnd,
    BottomCenter,
    TopCenter,
}

// ─── Component ──────────────────────────────────────────────

/**
 * A click-triggered popup that displays additional content anchored to a trigger element.
 *
 * The popover renders a card with shadow, border, and theme-aware colors. Press Escape
 * or click outside to dismiss. Automatically provides [LocalMinTouchTarget] of 0.dp inside
 * the popover to prevent inflated touch targets in dense content.
 *
 * @param expanded Whether the popover is currently visible.
 * @param onDismiss Callback invoked when the user requests dismissal (outside click or Escape).
 * @param modifier [Modifier] applied to the outer container wrapping trigger and popup.
 * @param label Accessibility pane title for the popover. Defaults to "Popover".
 * @param animation [PopoverAnimation] style for enter/exit transitions. Defaults to [PopoverAnimation.FadeExpand].
 * @param placement [PopoverPlacement] controlling where the popup appears relative to the trigger. Defaults to [PopoverPlacement.BottomStart].
 * @param minWidth Minimum width of the popover card. Defaults to 120.dp.
 * @param maxWidth Maximum width of the popover card. Defaults to 360.dp.
 * @param trigger Composable content that acts as the anchor element for the popover.
 * @param content Composable content rendered inside the popover card.
 */
@Composable
public fun Popover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Popover",
    animation: PopoverAnimation = PopoverAnimation.FadeExpand,
    placement: PopoverPlacement = PopoverPlacement.BottomStart,
    minWidth: Dp = 120.dp,
    maxWidth: Dp = 360.dp,
    trigger: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = KazTheme.motion
    val focusRequester = remember { FocusRequester() }

    val gap = with(LocalDensity.current) { KazTheme.spacing.xs.roundToPx() }
    val positionProvider = remember(placement, gap) { resolvePositionProvider(placement, gap) }

    var showPopup by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) showPopup = true
    }

    Box(modifier = modifier) {
        trigger()

        if (showPopup) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = onDismiss,
            ) {
                when (animation) {
                    PopoverAnimation.FadeExpand -> {
                        val expandFrom = resolveExpandFrom(placement)
                        AnimatedVisibility(
                            visible = expanded,
                            enter =
                                fadeIn(
                                    animationSpec =
                                        tween(
                                            motion.durationFast,
                                        ),
                                ) +
                                    expandVertically(
                                        animationSpec =
                                            tween(
                                                motion.durationDefault,
                                            ),
                                        expandFrom = expandFrom,
                                    ),
                            exit =
                                fadeOut(
                                    animationSpec =
                                        tween(
                                            motion.durationFast,
                                        ),
                                ) +
                                    shrinkVertically(
                                        animationSpec =
                                            tween(
                                                motion.durationDefault,
                                            ),
                                        shrinkTowards = expandFrom,
                                    ),
                        ) {
                            PopoverCard(
                                label = label,
                                onDismiss = onDismiss,
                                focusRequester = focusRequester,
                                minWidth = minWidth,
                                maxWidth = maxWidth,
                                content = content,
                            )
                        }
                    }

                    PopoverAnimation.Fade -> {
                        AnimatedVisibility(
                            visible = expanded,
                            enter =
                                fadeIn(
                                    animationSpec =
                                        tween(
                                            motion.durationDefault,
                                        ),
                                ),
                            exit =
                                fadeOut(
                                    animationSpec =
                                        tween(
                                            motion.durationDefault,
                                        ),
                                ),
                        ) {
                            PopoverCard(
                                label = label,
                                onDismiss = onDismiss,
                                focusRequester = focusRequester,
                                minWidth = minWidth,
                                maxWidth = maxWidth,
                                content = content,
                            )
                        }
                    }

                    PopoverAnimation.None -> {
                        PopoverCard(
                            label = label,
                            onDismiss = onDismiss,
                            focusRequester = focusRequester,
                            minWidth = minWidth,
                            maxWidth = maxWidth,
                            content = content,
                        )
                    }
                }

                if (!expanded) {
                    LaunchedEffect(Unit) {
                        if (animation != PopoverAnimation.None) delay(motion.durationDefault.toLong() + 50L)
                        showPopup = false
                    }
                }
            }
        }
    }
}

// ─── Private helpers ────────────────────────────────────────

@Composable
private fun PopoverCard(
    label: String,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    minWidth: Dp,
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    val colors = KazTheme.colors
    val shapes = KazTheme.shapes
    val spacing = KazTheme.spacing

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .semantics { paneTitle = label }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.Escape
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }.focusRequester(focusRequester)
                .focusable()
                .widthIn(min = minWidth, max = maxWidth)
                .shadow(KazTheme.elevation.high, shapes.md)
                .border(1.dp, colors.border, shapes.md)
                .background(colors.surface, shapes.md)
                .clip(shapes.md)
                .padding(spacing.lg),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onSurface,
            LocalTextStyle provides KazTheme.typography.small,
            LocalMinTouchTarget provides 0.dp,
        ) {
            content()
        }
    }
}

private fun resolvePositionProvider(
    placement: PopoverPlacement,
    gap: Int,
): PopupPositionProvider {
    val side =
        when (placement) {
            PopoverPlacement.BottomStart,
            PopoverPlacement.BottomEnd,
            PopoverPlacement.BottomCenter,
            -> PopupSide.Bottom

            PopoverPlacement.TopStart,
            PopoverPlacement.TopEnd,
            PopoverPlacement.TopCenter,
            -> PopupSide.Top
        }
    val align =
        when (placement) {
            PopoverPlacement.BottomStart, PopoverPlacement.TopStart -> PopupAlign.Start
            PopoverPlacement.BottomEnd, PopoverPlacement.TopEnd -> PopupAlign.End
            PopoverPlacement.BottomCenter, PopoverPlacement.TopCenter -> PopupAlign.Center
        }
    return AnchoredPopupPositionProvider(side = side, align = align, gap = gap)
}

private fun resolveExpandFrom(placement: PopoverPlacement): Alignment.Vertical =
    when (placement) {
        PopoverPlacement.BottomStart,
        PopoverPlacement.BottomEnd,
        PopoverPlacement.BottomCenter,
        -> Alignment.Top

        PopoverPlacement.TopStart,
        PopoverPlacement.TopEnd,
        PopoverPlacement.TopCenter,
        -> Alignment.Bottom
    }
