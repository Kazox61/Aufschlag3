package com.kazox.ui.components.stepper

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazox.ui.components.button.Button
import com.kazox.ui.components.button.ButtonSize
import com.kazox.ui.components.button.ButtonVariant
import com.kazox.ui.components.icon.Icon
import com.kazox.ui.components.icon.IconSize
import com.kazox.ui.components.icon.KazIcons
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import com.kazox.ui.foundation.KazTheme
import com.kazox.ui.foundation.LocalContentColor
import com.kazox.ui.foundation.modifier.LocalMinTouchTarget
import kotlinx.coroutines.delay

// ─── Variant ────────────────────────────────────────────────

/** Stepper container styles.
 * @property Outline Bordered container matching the Input field style.
 * @property Secondary Filled container with no border.
 * @property Ghost No container — just buttons and value, SwiftUI-like.
 */
public enum class StepperVariant {
    Outline,
    Secondary,
    Ghost,
}

// ─── Size ───────────────────────────────────────────────────

/** Stepper sizes.
 * @property Sm Compact — settings rows.
 * @property Default Standard.
 * @property Lg Large touch targets.
 */
public enum class StepperSize {
    Sm,
    Default,
    Lg,
}

// ─── Defaults ───────────────────────────────────────────────

/** Default values for [Stepper]. */
public object StepperDefaults {
    /** How long the button must be held before auto-repeat kicks in. */
    public const val RepeatInitialDelayMillis: Long = 400L

    /** Interval between steps while the button is held. */
    public const val RepeatIntervalMillis: Long = 80L
}

// ─── Internal Values ────────────────────────────────────────

@Immutable
internal data class StepperSizeValues(
    val containerHeight: Dp,
    val buttonSize: ButtonSize,
    val iconSize: IconSize,
    val valueMinWidth: Dp,
    val horizontalPadding: Dp,
    val valueVariant: TextVariant,
)

@Immutable
internal data class StepperContainerColors(
    val background: Color,
    val border: Color,
    val disabledBackground: Color,
    val disabledBorder: Color,
)

// ─── Component ──────────────────────────────────────────────

/**
 * KazUI stepper — a numeric +/− value control for settings rows.
 *
 * Composed from the kazui [Button] primitive (Ghost variant, Icon size) with
 * a hoisted interaction source, so the +/− buttons pick up the theme's
 * hover/press tokens and press-scale animation for free. Press-and-hold
 * auto-repeats after [StepperDefaults.RepeatInitialDelayMillis], then steps
 * every [StepperDefaults.RepeatIntervalMillis].
 *
 * The value slides up on increment and down on decrement, using the theme's
 * snap motion token.
 *
 * ```
 * Stepper(
 *     value = reps,
 *     onValueChange = { reps = it },
 *     min = 1,
 *     max = 50,
 *     label = "Repetitions",
 * )
 * ```
 *
 * @param value Current value. Clamped into [min]..[max] for display.
 * @param onValueChange Called with the new clamped value on every step.
 * @param modifier Modifier applied to the stepper container.
 * @param min Minimum value. The decrement button disables at this bound.
 * @param max Maximum value. The increment button disables at this bound.
 * @param step Amount added or removed per step. Must be positive.
 * @param variant Container style — [StepperVariant.Outline], Secondary, Ghost.
 * @param size Height and button size — [StepperSize.Sm], Default, Lg.
 * @param enabled Whether the stepper responds to input.
 * @param holdToRepeat Whether press-and-hold auto-repeats.
 * @param label Accessibility name for the value, e.g. "Repetitions". Buttons
 *   announce "Increase <label>"/"Decrease <label>"; the value reads "<label>: <value>".
 * @param valueFormatter Custom display formatting, e.g. `{ "$it min" }`.
 *   Defaults to the raw integer.
 * @param increaseDescription Accessibility label for the + button. Overrides the
 *   derived "Increase <label>" — use for localization, e.g. "Mehr".
 * @param decreaseDescription Accessibility label for the − button. Overrides the
 *   derived "Decrease <label>" — use for localization, e.g. "Weniger".
 */
@Composable
public fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    variant: StepperVariant = StepperVariant.Outline,
    size: StepperSize = StepperSize.Default,
    enabled: Boolean = true,
    holdToRepeat: Boolean = true,
    label: String = "",
    valueFormatter: ((Int) -> String)? = null,
    increaseDescription: String? = null,
    decreaseDescription: String? = null,
) {
    require(step > 0) { "step must be positive, was $step" }
    require(min <= max) { "min ($min) must be <= max ($max)" }

    val motion = KazTheme.motion
    val colors = KazTheme.colors
    val sizeValues = stepperSizeValues(size)
    val containerColors = resolveContainerColors(variant)

    val currentValue = value.coerceIn(min, max)
    val canDecrement = currentValue - step >= min
    val canIncrement = currentValue + step <= max

    // ─── Slide direction for the value change animation ─
    var slideDirection by remember { mutableIntStateOf(1) }
    val stepBy: (Int) -> Unit =
        { direction ->
            slideDirection = direction
            onValueChange((currentValue + direction * step).coerceIn(min, max))
        }

    // ─── Container colors (animated, same tween as Button) ──
    val animatedBackground by animateColorAsState(
        targetValue = if (enabled) containerColors.background else containerColors.disabledBackground,
        animationSpec = tween(motion.durationDefault),
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (enabled) containerColors.border else containerColors.disabledBorder,
        animationSpec = tween(motion.durationDefault),
    )

    val contentColor =
        if (enabled) colors.onBackground else colors.onBackground.copy(alpha = 0.5f)

    val showBorder = variant == StepperVariant.Outline
    val contentSpacing =
        if (variant == StepperVariant.Ghost) KazTheme.spacing.xs else KazTheme.spacing.none

    val decreaseText = decreaseDescription ?: if (label.isEmpty()) "Decrease" else "Decrease $label"
    val increaseText = increaseDescription ?: if (label.isEmpty()) "Increase" else "Increase $label"

    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = sizeValues.containerHeight)
                .then(
                    if (showBorder) {
                        Modifier.border(1.dp, animatedBorder, KazTheme.shapes.md)
                    } else {
                        Modifier
                    },
                ).then(
                    if (variant != StepperVariant.Ghost) {
                        Modifier.background(animatedBackground, KazTheme.shapes.md)
                    } else {
                        Modifier
                    },
                ).clip(KazTheme.shapes.md)
                .padding(horizontal = sizeValues.horizontalPadding),
        horizontalArrangement =
            Arrangement.spacedBy(
                contentSpacing,
                Alignment.CenterHorizontally,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalMinTouchTarget provides 0.dp,
        ) {
            StepperButton(
                icon = KazIcons.Minus,
                contentDescription = decreaseText,
                onClick = { stepBy(-1) },
                enabled = enabled && canDecrement,
                holdToRepeat = holdToRepeat,
                size = sizeValues.buttonSize,
                iconSize = sizeValues.iconSize,
            )

            AnimatedContent(
                targetState = currentValue,
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    // Theme's snap spring, built from raw tokens because
                    // KazMotion factory methods return the non-finite AnimationSpec.
                    val snapSpring =
                        spring<IntOffset>(
                            dampingRatio = motion.spatialDampingSnap,
                            stiffness = motion.spatialStiffnessSnap,
                        )
                    val increasing = slideDirection > 0
                    val slideIn =
                        slideInVertically(animationSpec = snapSpring) { fullHeight ->
                            if (increasing) fullHeight else -fullHeight
                        }
                    val slideOut =
                        slideOutVertically(animationSpec = snapSpring) { fullHeight ->
                            if (increasing) -fullHeight else fullHeight
                        }
                    slideIn togetherWith slideOut
                },
                label = "StepperValue",
            ) { animatedValue ->
                val displayText = valueFormatter?.invoke(animatedValue) ?: animatedValue.toString()
                Text(
                    text = displayText,
                    variant = sizeValues.valueVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = sizeValues.valueMinWidth)
                            .semantics {
                                if (label.isNotEmpty()) {
                                    contentDescription = "$label: $displayText"
                                }
                            },
                )
            }

            StepperButton(
                icon = KazIcons.Plus,
                contentDescription = increaseText,
                onClick = { stepBy(1) },
                enabled = enabled && canIncrement,
                holdToRepeat = holdToRepeat,
                size = sizeValues.buttonSize,
                iconSize = sizeValues.iconSize,
            )
        }
    }
}

// ─── Internal: Step Button ──────────────────────────────────

/**
 * One +/− button, built on the kazui [Button] primitive.
 *
 * Uses a hoisted [MutableInteractionSource] to observe the press state and
 * drive auto-repeat while held: after [StepperDefaults.RepeatInitialDelayMillis]
 * the [onClick] fires every [StepperDefaults.RepeatIntervalMillis]. The
 * release-click is suppressed once repeating has started, so a single hold
 * never double-steps.
 */
@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    holdToRepeat: Boolean,
    size: ButtonSize,
    iconSize: IconSize,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var didRepeat by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (!isPressed || !holdToRepeat) return@LaunchedEffect
        didRepeat = false
        delay(StepperDefaults.RepeatInitialDelayMillis)
        didRepeat = true
        while (true) {
            currentOnClick()
            delay(StepperDefaults.RepeatIntervalMillis)
        }
    }

    Button(
        onClick = {
            if (!didRepeat) currentOnClick()
            didRepeat = false
        },
        variant = ButtonVariant.Ghost,
        size = size,
        enabled = enabled,
        label = contentDescription,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            size = iconSize,
        )
    }
}

// ─── Internal: Size & Color Resolution ──────────────────────

@Composable
private fun stepperSizeValues(size: StepperSize): StepperSizeValues =
    when (size) {
        StepperSize.Sm ->
            StepperSizeValues(
                containerHeight = 32.dp,
                buttonSize = ButtonSize.Sm,
                iconSize = IconSize.Sm,
                valueMinWidth = 28.dp,
                horizontalPadding = KazTheme.spacing.none,
                valueVariant = TextVariant.Small,
            )

        StepperSize.Default ->
            StepperSizeValues(
                containerHeight = 36.dp,
                buttonSize = ButtonSize.Sm,
                iconSize = IconSize.Sm,
                valueMinWidth = 32.dp,
                horizontalPadding = KazTheme.spacing.none,
                valueVariant = TextVariant.Small,
            )

        StepperSize.Lg ->
            StepperSizeValues(
                containerHeight = 44.dp,
                buttonSize = ButtonSize.Default,
                iconSize = IconSize.Default,
                valueMinWidth = 40.dp,
                horizontalPadding = KazTheme.spacing.none,
                valueVariant = TextVariant.P,
            )
    }

@Composable
private fun resolveContainerColors(variant: StepperVariant): StepperContainerColors {
    val colors = KazTheme.colors
    return when (variant) {
        StepperVariant.Outline ->
            StepperContainerColors(
                background = colors.background,
                border = colors.border,
                disabledBackground = colors.background,
                disabledBorder = colors.border.copy(alpha = 0.5f),
            )

        StepperVariant.Secondary ->
            StepperContainerColors(
                background = colors.secondary,
                border = Color.Transparent,
                disabledBackground = colors.secondary.copy(alpha = 0.5f),
                disabledBorder = Color.Transparent,
            )

        StepperVariant.Ghost ->
            StepperContainerColors(
                background = Color.Transparent,
                border = Color.Transparent,
                disabledBackground = Color.Transparent,
                disabledBorder = Color.Transparent,
            )
    }
}
