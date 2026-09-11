package com.kazox.ui.components.togglegroup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import com.kazox.ui.foundation.LocalContentColor
import com.kazox.ui.foundation.KazTheme
import com.kazox.ui.foundation.modifier.minTouchTarget

// ─── Variant ───────────────────────────────────────────────

/** Muted bg when selected; Outline adds a 1dp border. */
public enum class ToggleGroupVariant {
    /** Muted background when selected, transparent otherwise. */
    Default,

    /** 1dp border always visible; muted background when selected. */
    Outline,
}

// ─── Animation ────────────────────────────────────────────

/** Animation strategy for selection color transitions. */
public enum class ToggleGroupAnimation {
    /** Spring-based color transition (default). */
    Spring,

    /** Smooth eased tween transition. */
    Tween,

    /** Instant change, no animation. */
    None,
}

// ─── ToggleGroup ───────────────────────────────────────────

/**
 * A horizontal group container for [ToggleGroupItem] buttons with selectable-group semantics.
 *
 * Applies `selectableGroup()` semantics so screen readers treat child items
 * as a mutually exclusive selection set. Items are spaced using [KazTheme.spacing.xs].
 *
 * ```
 * var selected by remember { mutableStateOf("left") }
 * ToggleGroup {
 *     ToggleGroupItem(text = "Left", selected = selected == "left", onClick = { selected = "left" })
 *     ToggleGroupItem(text = "Center", selected = selected == "center", onClick = { selected = "center" })
 *     ToggleGroupItem(text = "Right", selected = selected == "right", onClick = { selected = "right" })
 * }
 * ```
 *
 * @param modifier Modifier applied to the outer Row container.
 * @param content Composable slot for [ToggleGroupItem] children.
 */
@Composable
public fun ToggleGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(KazTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

// ─── ToggleGroupItem ───────────────────────────────────────

/**
 * An individual selectable item inside a [ToggleGroup].
 *
 * Renders a box with animated background and foreground color transitions
 * that reflect the current selection state. The resolved foreground color
 * is provided to children via [LocalContentColor], so Text and Icon composables
 * inside the [content] lambda automatically inherit the correct color.
 *
 * ```
 * ToggleGroupItem(
 *     selected = isSelected,
 *     onClick = { onSelect() },
 *     label = "Bold",
 * ) {
 *     Icon(KazIcons.Edit, contentDescription = null)
 * }
 * ```
 *
 * @param selected Whether this item is currently selected.
 * @param onClick Called when the user taps this item.
 * @param modifier Modifier applied to the item Box container.
 * @param variant Visual style -- [ToggleGroupVariant.Default] or [ToggleGroupVariant.Outline].
 *   Defaults to [ToggleGroupVariant.Default].
 * @param animation Color transition style -- [ToggleGroupAnimation.Spring], Tween, None.
 *   Defaults to [ToggleGroupAnimation.Spring].
 * @param label Accessibility content description for screen readers. Defaults to empty.
 * @param selectedColor Override foreground color when selected. [Color.Unspecified] uses theme default.
 * @param unselectedColor Override foreground color when unselected. [Color.Unspecified] uses theme default.
 * @param content Composable content displayed inside the item.
 */
@Composable
public fun ToggleGroupItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ToggleGroupVariant = ToggleGroupVariant.Default,
    animation: ToggleGroupAnimation = ToggleGroupAnimation.Spring,
    label: String = "",
    selectedColor: Color = Color.Unspecified,
    unselectedColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = KazTheme.colors
    val motion = KazTheme.motion
    val shape = KazTheme.shapes.md

    val resolved =
        resolveColors(
            variant = variant,
            selected = selected,
            selectedColorOverride = selectedColor,
            unselectedColorOverride = unselectedColor,
        )

    // ─── Resolve animation spec ───────────────────────────
    val colorAnimSpec: AnimationSpec<Color> = resolveAnimSpec(animation, motion)

    // ─── Animated colors (from theme motion tokens) ───────
    // An unselected item has a transparent background, so a press on one produced no visible
    // change at all — indication is null and a 0.97 scale on a text chip is imperceptible. A
    // faint fill while held is the only acknowledgement the tap registered.
    val targetBackground =
        if (!selected && isPressed) colors.muted.copy(alpha = 0.6f) else resolved.background

    val animatedBackground by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = colorAnimSpec,
    )

    val animatedForeground by animateColorAsState(
        targetValue = resolved.foreground,
        animationSpec = colorAnimSpec,
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) motion.pressScaleSubtle else 1f,
        animationSpec = motion.effectsFast(),
        label = "toggleGroupPress",
    )

    // Always applied, never gated on the target colour. Dropping the modifier the moment the
    // target became transparent removed the deselected item's pill in a single frame while the
    // newly selected one was still springing in — so for ~100ms nothing looked selected at all.
    // A transparent colour draws nothing on its own; the modifier can stay.
    val backgroundModifier = Modifier.background(animatedBackground, shape)

    val borderModifier =
        if (resolved.border != Color.Transparent) {
            Modifier.border(1.dp, resolved.border, shape)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .minTouchTarget()
                .then(borderModifier)
                .then(backgroundModifier)
                .clip(shape)
                .graphicsLayer {
                    // Indication is null, so without this a tap on an already-subtle grey pill
                    // gives no acknowledgement whatsoever until the colour finishes animating.
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha = if (isHovered && !selected) motion.hoverAlpha else 1f
                }.selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(
                    horizontal = KazTheme.spacing.md,
                    vertical = KazTheme.spacing.sm,
                ).semantics {
                    this.selected = selected
                    if (label.isNotEmpty()) {
                        contentDescription = label
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides animatedForeground) {
            content()
        }
    }
}

/**
 * Convenience overload of [ToggleGroupItem] that renders a text label.
 *
 * Equivalent to wrapping a [Text] composable inside the content lambda of the
 * generic [ToggleGroupItem] overload. The [text] value is also used as the
 * accessibility content description.
 *
 * ```
 * ToggleGroupItem(
 *     text = "Bold",
 *     selected = isBold,
 *     onClick = { toggleBold() },
 * )
 * ```
 *
 * @param text The label to display and use as accessibility description.
 * @param selected Whether this item is currently selected.
 * @param onClick Called when the user taps this item.
 * @param modifier Modifier applied to the item Box container.
 * @param variant Visual style -- [ToggleGroupVariant.Default] or [ToggleGroupVariant.Outline].
 *   Defaults to [ToggleGroupVariant.Default].
 * @param animation Color transition style -- [ToggleGroupAnimation.Spring], Tween, None.
 *   Defaults to [ToggleGroupAnimation.Spring].
 * @param selectedColor Override foreground color when selected. [Color.Unspecified] uses theme default.
 * @param unselectedColor Override foreground color when unselected. [Color.Unspecified] uses theme default.
 */
@Composable
public fun ToggleGroupItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ToggleGroupVariant = ToggleGroupVariant.Default,
    animation: ToggleGroupAnimation = ToggleGroupAnimation.Spring,
    selectedColor: Color = Color.Unspecified,
    unselectedColor: Color = Color.Unspecified,
) {
    ToggleGroupItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        animation = animation,
        label = text,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
    ) {
        Text(
            text = text,
            variant = TextVariant.Small,
        )
    }
}

// ─── Internal: Color Resolution ─────────────────────────────

private data class ToggleGroupColors(
    val background: Color,
    val foreground: Color,
    val border: Color,
)

@Composable
private fun resolveColors(
    variant: ToggleGroupVariant,
    selected: Boolean,
    selectedColorOverride: Color = Color.Unspecified,
    unselectedColorOverride: Color = Color.Unspecified,
): ToggleGroupColors {
    val colors = KazTheme.colors

    val baseForeground =
        when {
            selected && selectedColorOverride != Color.Unspecified -> {
                selectedColorOverride
            }

            !selected && unselectedColorOverride != Color.Unspecified -> {
                unselectedColorOverride
            }

            selected -> {
                colors.onBackground
            }

            else -> {
                colors.onMuted
            }
        }

    // The unselected background is muted at zero alpha, NOT Color.Transparent.
    //
    // Color.Transparent is Color(0x00000000) — transparent *black*. Animating between it and a
    // light muted fill interpolates the colour channels from black upward while alpha rises, so
    // both the outgoing and the incoming chip visibly darken mid-transition: measured at #ABABAB
    // against an #F5F5F5 background. Keeping the hue fixed and moving only alpha means the fill
    // fades straight in and out with nothing to see on the way.
    val fadedOut = colors.muted.copy(alpha = 0f)

    return when (variant) {
        ToggleGroupVariant.Default -> {
            ToggleGroupColors(
                background =
                    if (selected) colors.muted else fadedOut,
                foreground = baseForeground,
                border = Color.Transparent,
            )
        }

        ToggleGroupVariant.Outline -> {
            ToggleGroupColors(
                background =
                    if (selected) colors.muted else fadedOut,
                foreground = baseForeground,
                border = colors.border,
            )
        }
    }
}

// ─── Internal: Animation Spec Resolution ──────────────────

@Composable
private fun <T> resolveAnimSpec(
    animation: ToggleGroupAnimation,
    motion: com.kazox.ui.foundation.KazMotion,
): AnimationSpec<T> =
    when (animation) {
        // spatialSnap, not spatialDefault: the default is a low-stiffness bouncy spring meant
        // for position and size. On a colour it reads as the selection drifting in rather than
        // responding, which is what made tapping feel like nothing happened.
        ToggleGroupAnimation.Spring -> motion.spatialSnap()
        ToggleGroupAnimation.Tween -> motion.effectsFast()
        ToggleGroupAnimation.None -> snap()
    }
