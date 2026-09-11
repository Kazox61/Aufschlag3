package com.kazox.ui.components.alertdialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import com.kazox.ui.components.button.Button
import com.kazox.ui.components.button.ButtonVariant
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import com.kazox.ui.foundation.LocalContentColor
import com.kazox.ui.foundation.LocalTextStyle
import com.kazox.ui.foundation.KazMotion
import com.kazox.ui.foundation.KazTheme

// ─── Animation Enum ─────────────────────────────────────────

/**
 * Animation style for [AlertDialog] enter/exit transitions.
 */
public enum class AlertDialogAnimation {
    /** Fade + scale up from 0.95. */
    FadeScale,

    /** Opacity-only fade. */
    Fade,

    /** No animation. */
    None,
}

// ─── Component ──────────────────────────────────────────────

/**
 * A confirmation dialog that requires explicit user action to dismiss.
 *
 * Unlike [Dialog][com.kazox.ui.components.dialog.Dialog], clicking the scrim does not
 * dismiss an alert dialog. The user must interact with the action buttons or press Escape.
 * Auto-focuses on open and traps keyboard focus.
 *
 * @param open Whether the alert dialog is currently visible.
 * @param onDismiss Callback invoked when the user presses Escape or the cancel action.
 * @param onConfirm Callback invoked when the user confirms the action.
 * @param modifier [Modifier] applied to the dialog card container.
 * @param label Accessibility pane title announced by screen readers. Defaults to "Alert Dialog".
 * @param animation [AlertDialogAnimation] style for enter/exit transitions. Defaults to [AlertDialogAnimation.FadeScale].
 * @param scrimColor [Color] of the backdrop overlay behind the dialog. Defaults to [KazTheme.colors.scrim].
 * @param maxWidth Maximum width of the dialog card. Defaults to 520.dp. A margin is always kept
 *   around the card, so on screens narrower than the maximum it does not touch the edges.
 * @param content Composable content rendered inside the dialog card.
 */
@Composable
public fun AlertDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Alert Dialog",
    animation: AlertDialogAnimation = AlertDialogAnimation.FadeScale,
    scrimColor: Color = KazTheme.colors.scrim,
    maxWidth: Dp = 520.dp,
    content: @Composable () -> Unit,
) {
    var showPopup by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) showPopup = true
    }
    if (!showPopup) return

    val colors = KazTheme.colors
    val shapes = KazTheme.shapes
    val spacing = KazTheme.spacing
    val motion = KazTheme.motion

    val (cardEnter, cardExit) =
        resolveAlertDialogTransition(
            animation,
            motion,
        )

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val focusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // ─── Scrim (non-dismissing) ───────────────
            AnimatedVisibility(
                visible = open,
                enter =
                    fadeIn(
                        animationSpec = tween(motion.durationEnter),
                    ),
                exit =
                    fadeOut(
                        animationSpec = tween(motion.durationEnter),
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(scrimColor)
                            .clickable(
                                interactionSource =
                                    remember {
                                        MutableInteractionSource()
                                    },
                                indication = null,
                                onClick = {
                                    // Intentionally empty — alert dialogs
                                    // must not dismiss on scrim click.
                                },
                            ),
                )
            }

            // ─── Dialog card ──────────────────────────
            AnimatedVisibility(
                visible = open,
                enter = cardEnter,
                exit = cardExit,
            ) {
                Column(
                    modifier =
                        modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.key == Key.Escape &&
                                    event.type == KeyEventType.KeyDown
                                ) {
                                    onDismiss()
                                    true
                                } else {
                                    false
                                }
                            }.padding(horizontal = spacing.lg)
                                .widthIn(max = maxWidth)
                            .semantics(mergeDescendants = true) {
                                paneTitle = label
                                contentDescription = label
                                dismiss {
                                    onDismiss()
                                    true
                                }
                            }.shadow(KazTheme.elevation.high, shapes.lg)
                            .border(1.dp, colors.border, shapes.lg)
                            .background(colors.surface, shapes.lg)
                            .clip(shapes.lg)
                            .padding(spacing.xl),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            spacing.md,
                        ),
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.onSurface,
                        LocalTextStyle provides KazTheme.typography.p,
                    ) {
                        content()
                    }
                }
            }

            // Auto-focus the dialog card when opened
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            // ─── Cleanup: remove Popup after exit animation ──
            if (!open) {
                LaunchedEffect(Unit) {
                    delay(motion.durationEnter.toLong() + 50L)
                    showPopup = false
                }
            }
        }
    }
}

// ─── Structured Sections ────────────────────────────────────

/**
 * Structured header section for an [AlertDialog] with title and optional description.
 *
 * The title is rendered with heading semantics for accessibility.
 *
 * @param title Primary heading text displayed in the alert dialog header.
 * @param modifier [Modifier] applied to the header column.
 * @param description Optional secondary text shown below the title in muted style. Defaults to empty.
 */
@Composable
public fun AlertDialogHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(
                KazTheme.spacing.xs,
            ),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            variant = TextVariant.Large,
        )
        if (description.isNotEmpty()) {
            Text(
                text = description,
                variant = TextVariant.Muted,
            )
        }
    }
}

/**
 * Footer row for action buttons inside an [AlertDialog], aligned to the end.
 *
 * Typically contains [AlertDialogCancel] and [AlertDialogAction] buttons.
 *
 * @param modifier [Modifier] applied to the footer row.
 * @param content Composable action buttons (e.g., Cancel and Confirm).
 */
@Composable
public fun AlertDialogFooter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = KazTheme.spacing.sm),
        horizontalArrangement =
            Arrangement.spacedBy(
                KazTheme.spacing.sm,
                Alignment.End,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

// ─── Action Buttons ─────────────────────────────────────────

/**
 * Pre-styled cancel button for an [AlertDialog] using the Outline button variant.
 *
 * @param onClick Callback invoked when the cancel button is clicked.
 * @param modifier [Modifier] applied to the button.
 * @param text Label text for the cancel button. Defaults to "Cancel".
 */
@Composable
public fun AlertDialogCancel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Cancel",
) {
    Button(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Outline,
    )
}

// ─── Action Variant ─────────────────────────────────────────

public enum class AlertDialogActionVariant {
    /** Primary-colored confirm button. */
    Default,

    /** Red-tinted button for dangerous actions. */
    Destructive,
}

/**
 * Pre-styled confirm/action button for an [AlertDialog].
 *
 * Supports both default (primary) and destructive styling via [AlertDialogActionVariant].
 *
 * @param text Label text for the action button.
 * @param onClick Callback invoked when the action button is clicked.
 * @param modifier [Modifier] applied to the button.
 * @param variant [AlertDialogActionVariant] controlling the button style. Defaults to [AlertDialogActionVariant.Default].
 */
@Composable
public fun AlertDialogAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AlertDialogActionVariant =
        AlertDialogActionVariant.Default,
) {
    val buttonVariant =
        when (variant) {
            AlertDialogActionVariant.Default -> {
                ButtonVariant.Default
            }

            AlertDialogActionVariant.Destructive -> {
                ButtonVariant.Destructive
            }
        }

    Button(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = buttonVariant,
    )
}

// ─── Internal: Transition Resolution ────────────────────────

private fun resolveAlertDialogTransition(
    animation: AlertDialogAnimation,
    motion: KazMotion,
): Pair<EnterTransition, ExitTransition> =
    when (animation) {
        AlertDialogAnimation.FadeScale -> {
            val enter =
                fadeIn(
                    animationSpec = tween(motion.durationEnter),
                ) +
                    scaleIn(
                        initialScale = motion.overlayScaleIn,
                        animationSpec = tween(motion.durationEnter),
                    )
            val exit =
                fadeOut(
                    animationSpec = tween(motion.durationEnter),
                ) +
                    scaleOut(
                        targetScale = motion.overlayScaleIn,
                        animationSpec = tween(motion.durationEnter),
                    )
            enter to exit
        }

        AlertDialogAnimation.Fade -> {
            val enter =
                fadeIn(
                    animationSpec = tween(motion.durationEnter),
                )
            val exit =
                fadeOut(
                    animationSpec = tween(motion.durationEnter),
                )
            enter to exit
        }

        AlertDialogAnimation.None -> {
            val enter = fadeIn(animationSpec = tween(motion.durationInstant))
            val exit = fadeOut(animationSpec = tween(motion.durationInstant))
            enter to exit
        }
    }
