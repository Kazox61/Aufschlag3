package com.kazox.ui.components.navigationbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kazox.ui.components.icon.Icon
import com.kazox.ui.components.icon.IconSize
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import com.kazox.ui.foundation.KazTheme

// ─── Animation ────────────────────────────────────────────────

public enum class NavigationBarAnimation {
    /** Spring-based transitions (default). */
    Spring,

    /** Smooth eased tween transitions. */
    Tween,

    /** Instant, no animation. */
    None,
}

// ─── Dimensions ───────────────────────────────────────────────

/** Height of the docked bar. */
private val DockedBarHeight = 80.dp

/** Width of the docked selection pill. */
private val DockedIndicatorWidth = 48.dp

/** Height of the docked selection pill. */
private val DockedIndicatorHeight = 32.dp

/** Height of the floating capsule, and the default diameter of [NavigationBarAction]. */
private val FloatingBarHeight = 64.dp

/**
 * Size of the square chip behind the selected item when floating. Same width and height, so
 * shapes.full renders a circle, and only [FloatingBarHeight] minus this separates the chip from
 * the capsule edge — keep the difference small or the chip looks lost inside the bar.
 */
private val FloatingIndicatorWidth = 48.dp

/** Height of the square chip behind the selected item when floating. */
private val FloatingIndicatorHeight = 48.dp

// ─── Variant ──────────────────────────────────────────────────

/**
 * Presentation style of a [NavigationBar].
 *
 * @property Docked Full-width bar pinned to the bottom edge with a top divider.
 *   Items show their labels and a wide indicator pill. The classic layout.
 * @property Floating A capsule that hovers above the content with a shadow and
 *   margins on every side. Items are icon-only and the selected one sits on a
 *   raised circular chip. Pair it with [NavigationBar]'s `action` slot for the
 *   detached primary-action button.
 */
public enum class NavigationBarVariant {
    Docked,
    Floating,
}

// ─── Internal: variant-derived presentation ───────────────────

/**
 * Presentation values a [NavigationBar] hands down to its [NavigationBarItem]s so
 * items render correctly for the surrounding variant without the caller repeating
 * the variant at every call site.
 */
@Immutable
internal data class NavigationBarStyle(
    val indicatorWidth: Dp,
    val indicatorHeight: Dp,
    val indicatorElevation: Dp,
    val indicatorColor: Color,
    val showLabels: Boolean,
    /**
     * Fixed item width in the floating variant, so items are square ([FloatingBarHeight] wide
     * and tall). Null lets items share the row width via [RowScope.weight].
     */
    val itemWidth: Dp?,
    /**
     * When true the bar draws one shared indicator that slides between items, and items skip
     * drawing their own. When false each item fades its indicator in place.
     */
    val slidingIndicator: Boolean,
)

internal val LocalNavigationBarStyle =
    staticCompositionLocalOf<NavigationBarStyle?> { null }

/**
 * Where the shared sliding indicator should sit. Items publish their own center as they become
 * selected; the bar animates the indicator toward whatever center is currently published.
 *
 * Coordinates are in pixels, relative to the row that holds the items.
 */
@Stable
internal class NavigationBarIndicatorState {
    var selectedCenterX: Float? by mutableStateOf(null)
}

internal val LocalNavigationBarIndicatorState =
    staticCompositionLocalOf<NavigationBarIndicatorState?> { null }

/** Docked presentation, used when an item renders outside a [NavigationBar]. */
private fun DefaultNavigationBarStyle(indicatorColor: Color): NavigationBarStyle =
    NavigationBarStyle(
        indicatorWidth = DockedIndicatorWidth,
        indicatorHeight = DockedIndicatorHeight,
        indicatorElevation = 0.dp,
        indicatorColor = indicatorColor,
        showLabels = true,
        itemWidth = null,
        slidingIndicator = false,
    )

// ─── NavigationBar ────────────────────────────────────────────

/**
 * A bottom navigation bar, either docked to the screen edge or floating above the content.
 *
 * Place [NavigationBarItem] composables inside [content]; they pick up the indicator shape
 * and label visibility that match [variant] automatically.
 *
 * [NavigationBarVariant.Docked] renders a full-width 80dp row with a top divider.
 * [NavigationBarVariant.Floating] renders a shadowed capsule inset from every edge, with
 * icon-only items and a raised circular chip behind the selected one. Overlay it on the
 * content (e.g. in a [Box] aligned to [Alignment.BottomCenter]) rather than stacking it
 * below — the floating style is meant to hover over scrolling content.
 *
 * ```
 * NavigationBar(
 *     variant = NavigationBarVariant.Floating,
 *     action = { NavigationBarAction(icon = KazIcons.Plus, onClick = ::onAdd) },
 * ) {
 *     NavigationBarItem(selected = tab == Home, onClick = { tab = Home }, icon = KazIcons.Home, label = "Home")
 * }
 * ```
 *
 * @param modifier [Modifier] applied to the navigation bar container.
 * @param variant Presentation style. Defaults to [NavigationBarVariant.Docked].
 * @param containerColor Background color of the bar. When [Color.Unspecified], resolves to
 *   [KazTheme.colors].background for [NavigationBarVariant.Docked] and
 *   [KazTheme.colors].muted for [NavigationBarVariant.Floating].
 * @param indicatorColor Color of the selection indicator behind the active item. When
 *   [Color.Unspecified], resolves to [KazTheme.colors].secondary when docked and
 *   [KazTheme.colors].background when floating. Individual items may still override it.
 * @param showLabels Whether items render their labels. When null, derives from [variant]:
 *   true when docked, false when floating.
 * @param elevation Shadow elevation of the floating capsule. Ignored when docked. Flat
 *   ([KazTheme.elevation].none) by default; pass e.g. [KazTheme.elevation].medium to raise it.
 * @param animation Motion style for the sliding indicator in the floating variant. Item-level
 *   `animation` still governs each item's own icon, label, and press transitions.
 * @param contentPadding Margin around the bar. When null, resolves to zero for docked and,
 *   for floating, to `lg` horizontal with `md` top — the bottom margin is `md` on a bare edge
 *   but drops to zero where the environment provides a bottom safe area (whose inset is meant
 *   to supply the clearance).
 * @param action Optional trailing action, rendered outside the capsule when floating and at
 *   the end of the row when docked. Use [NavigationBarAction] for the standard circular button.
 * @param content Row content lambda for [NavigationBarItem] composables.
 */
@Composable
public fun NavigationBar(
    modifier: Modifier = Modifier,
    variant: NavigationBarVariant = NavigationBarVariant.Docked,
    containerColor: Color = Color.Unspecified,
    indicatorColor: Color = Color.Unspecified,
    showLabels: Boolean? = null,
    elevation: Dp = Dp.Unspecified,
    animation: NavigationBarAnimation = NavigationBarAnimation.Spring,
    contentPadding: PaddingValues? = null,
    action: @Composable (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = KazTheme.colors
    val spacing = KazTheme.spacing
    val floating = variant == NavigationBarVariant.Floating

    val resolvedContainer =
        when {
            containerColor != Color.Unspecified -> containerColor
            floating -> colors.muted
            else -> colors.background
        }
    val resolvedIndicator =
        when {
            indicatorColor != Color.Unspecified -> indicatorColor
            floating -> colors.background
            else -> colors.secondary
        }

    val style =
        NavigationBarStyle(
            indicatorWidth = if (floating) FloatingIndicatorWidth else DockedIndicatorWidth,
            indicatorHeight = if (floating) FloatingIndicatorHeight else DockedIndicatorHeight,
            indicatorElevation = 0.dp,
            indicatorColor = resolvedIndicator,
            showLabels = showLabels ?: !floating,
            itemWidth = if (floating) FloatingBarHeight else null,
            slidingIndicator = floating,
        )

    val indicatorState = remember { NavigationBarIndicatorState() }

    CompositionLocalProvider(
        LocalNavigationBarStyle provides style,
        LocalNavigationBarIndicatorState provides if (floating) indicatorState else null,
    ) {
        if (floating) {
            val resolvedElevation =
                if (elevation != Dp.Unspecified) elevation else KazTheme.elevation.none
            // Default bottom margin adapts to the environment: a scaffold that lifts the bar
            // by the safe-area inset already provides the clearance, so the capsule needs none
            // of its own there — on a bare edge without a safe area (desktop, previews) the
            // regular margin keeps it off the screen edge.
            val safeBottom =
                WindowInsets.safeDrawing.getBottom(LocalDensity.current)
            val padding =
                contentPadding
                    ?: PaddingValues(
                        start = spacing.lg,
                        top = spacing.md,
                        end = spacing.lg,
                        bottom = if (safeBottom > 0) spacing.none else spacing.md,
                    )

            val indicatorSizePx = with(LocalDensity.current) { style.indicatorWidth.toPx() }
            // The bubble travels between fixed slots, so it must not overshoot past the end
            // item and out of the capsule — Spring here means "no bounce", not the bouncier
            // motion.spatialDefault() that items use for scale and fade.
            val slideSpec: AnimationSpec<Float> =
                when (animation) {
                    NavigationBarAnimation.Spring -> KazTheme.motion.spatialSnap()
                    NavigationBarAnimation.Tween -> KazTheme.motion.effectsDefault()
                    NavigationBarAnimation.None -> snap()
                }
            val centerX = indicatorState.selectedCenterX

            // A single indicator owned by the bar, animated toward the selected item's center.
            // The first placement snaps so the bubble does not slide in from the left edge on
            // the very first frame.
            val offsetX = remember { Animatable(0f) }
            var placed by remember { mutableStateOf(false) }
            // Keyed on the target only. `slideSpec` is a fresh instance on every recomposition
            // (spring()/tween() build a new object and do not implement equals), so keying on it
            // would restart — and therefore cancel — the slide on every frame.
            val currentSpec by rememberUpdatedState(slideSpec)
            LaunchedEffect(centerX) {
                val target = centerX ?: return@LaunchedEffect
                if (placed) {
                    offsetX.animateTo(target, currentSpec)
                } else {
                    offsetX.snapTo(target)
                    placed = true
                }
            }

            // The capsule wraps its square items instead of stretching: the row centers the
            // capsule and action button as a group, so the bar is only as wide as it needs to be.
            Row(
                modifier = modifier.fillMaxWidth().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(FloatingBarHeight)
                            .then(
                                if (resolvedElevation > 0.dp) {
                                    Modifier.shadow(resolvedElevation, KazTheme.shapes.full)
                                } else {
                                    Modifier
                                },
                            ).clip(KazTheme.shapes.full)
                            .background(resolvedContainer, KazTheme.shapes.full)
                            .semantics { isTraversalGroup = true },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        x = (offsetX.value - indicatorSizePx / 2f).roundToInt(),
                                        y = 0,
                                    )
                                }.width(style.indicatorWidth)
                                .height(style.indicatorHeight)
                                .graphicsLayer { alpha = if (placed) 1f else 0f }
                                .background(resolvedIndicator, KazTheme.shapes.full),
                    )

                    Row(
                        modifier = Modifier.height(FloatingBarHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        content()
                    }
                }
                action?.invoke()
            }
        } else {
            val borderColor = colors.border
            val dividerHeight = 1.dp

            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = dividerHeight.toPx(),
                            )
                        }.background(resolvedContainer)
                        .then(contentPadding?.let { Modifier.padding(it) } ?: Modifier),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(DockedBarHeight)
                            .padding(horizontal = spacing.sm)
                            .semantics { isTraversalGroup = true },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    content()
                    action?.invoke()
                }
            }
        }
    }
}

// ─── NavigationBarAction ──────────────────────────────────────

/**
 * The circular action button that sits beside a floating [NavigationBar].
 *
 * Visually detached from the capsule and filled with [KazTheme.colors].primary, this is the
 * screen's primary action — typically "add" or "compose". Pass it to [NavigationBar]'s
 * `action` slot.
 *
 * @param icon Icon rendered in the center of the button.
 * @param onClick Callback invoked when the button is clicked.
 * @param modifier [Modifier] applied to the button.
 * @param contentDescription Accessibility label. Pass null only when a sibling already
 *   describes the action.
 * @param enabled Whether the button is interactive. Defaults to true.
 * @param size Diameter of the button. Defaults to match the floating bar's height.
 * @param containerColor Background color. Defaults to [KazTheme.colors].primary.
 * @param contentColor Icon color. Defaults to [KazTheme.colors].onPrimary.
 * @param elevation Shadow elevation. Flat ([KazTheme.elevation].none) by default, matching the
 *   floating bar; pass e.g. [KazTheme.elevation].medium to raise it.
 * @param animation [NavigationBarAnimation] style for the press transition.
 */
@Composable
public fun NavigationBarAction(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    size: Dp = FloatingBarHeight,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    elevation: Dp = Dp.Unspecified,
    animation: NavigationBarAnimation = NavigationBarAnimation.Spring,
) {
    val colors = KazTheme.colors
    val motion = KazTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val resolvedContainer =
        if (containerColor != Color.Unspecified) containerColor else colors.primary
    val resolvedContent =
        if (contentColor != Color.Unspecified) contentColor else colors.onPrimary
    val resolvedElevation =
        if (elevation != Dp.Unspecified) elevation else KazTheme.elevation.none

    val floatAnimSpec: AnimationSpec<Float> = resolveAnimSpec(animation, motion)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) motion.pressScaleSubtle else 1f,
        animationSpec = floatAnimSpec,
    )

    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.then(
                    if (resolvedElevation > 0.dp) {
                        Modifier.shadow(resolvedElevation, KazTheme.shapes.full)
                    } else {
                        Modifier
                    },
                ).background(resolvedContainer, KazTheme.shapes.full)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    enabled = enabled,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    if (contentDescription != null) this.contentDescription = contentDescription
                    if (!enabled) disabled()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedContent,
            size = IconSize.Lg,
        )
    }
}

// ─── NavigationBarItem (content lambda) ───────────────────────

/**
 * A navigation bar item with icon, optional label, and animated indicator pill.
 *
 * This is the content-lambda overload that accepts composable lambdas for icon, label,
 * and selected icon. Features animated indicator pill, press scale, hover highlight,
 * and label fade transitions.
 *
 * @param selected Whether this item is currently selected.
 * @param onClick Callback invoked when the item is clicked.
 * @param icon Composable icon displayed in the default (unselected) state.
 * @param modifier [Modifier] applied to the item container.
 * @param label Optional composable label displayed below the icon. Pass null to hide.
 * @param selectedIcon Optional composable icon displayed when selected. Falls back to [icon] if null.
 * @param enabled Whether the item is interactive. Defaults to true.
 * @param alwaysShowLabel Whether to always show the label or only when selected. Defaults to true.
 * @param animation [NavigationBarAnimation] style for state transitions. Defaults to [NavigationBarAnimation.Spring].
 * @param indicatorColor Color of the selection indicator pill. Defaults to [KazTheme.colors.secondary] when [Color.Unspecified].
 */
@Composable
public fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    selectedIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    alwaysShowLabel: Boolean = true,
    animation: NavigationBarAnimation = NavigationBarAnimation.Spring,
    indicatorColor: Color = Color.Unspecified,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = KazTheme.colors
    val motion = KazTheme.motion

    // Presentation handed down by the enclosing NavigationBar; falls back to the docked
    // look so a bare item still renders sensibly outside a bar.
    val style = LocalNavigationBarStyle.current ?: DefaultNavigationBarStyle(colors.secondary)
    val indicatorState = LocalNavigationBarIndicatorState.current

    // With a sliding indicator the bar draws the highlight, so the item only has to report
    // where it sits. Position is recorded on every placement and published whenever this item
    // becomes the selected one — placement alone would not re-fire on a selection change.
    var centerX by remember { mutableStateOf(0f) }
    if (style.slidingIndicator) {
        LaunchedEffect(selected, centerX, indicatorState) {
            if (selected && centerX > 0f) indicatorState?.selectedCenterX = centerX
        }
    }

    // ─── Resolve animation specs ──────────────────────────
    val floatAnimSpec: AnimationSpec<Float> = resolveAnimSpec(animation, motion)
    val dpAnimSpec: AnimationSpec<Dp> = resolveAnimSpec(animation, motion)
    val fastFloatSpec: AnimationSpec<Float> =
        if (animation == NavigationBarAnimation.Tween) {
            motion.effectsFast()
        } else {
            floatAnimSpec
        }

    val resolvedIndicator =
        if (indicatorColor != Color.Unspecified) {
            indicatorColor
        } else {
            style.indicatorColor
        }

    // ─── Indicator animation ─────────────────────────────
    // In-place indicators widen from the item's center, so only the width animates. Unused
    // when the bar owns a sliding indicator.
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) style.indicatorWidth else 0.dp,
        animationSpec = dpAnimSpec,
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = floatAnimSpec,
    )

    // ─── Label fade animation ────────────────────────────
    // The bar gates labels entirely (the floating variant is icon-only); alwaysShowLabel
    // then chooses between "always" and "only while selected".
    val showLabel = style.showLabels && (alwaysShowLabel || selected)
    val labelAlpha by animateFloatAsState(
        targetValue = if (showLabel && label != null) 1f else 0f,
        animationSpec = floatAnimSpec,
    )
    val labelOffset by animateDpAsState(
        targetValue = if (showLabel && label != null) 0.dp else 4.dp,
        animationSpec = dpAnimSpec,
    )

    // ─── Press scale ─────────────────────────────────────
    val pressScale by animateFloatAsState(
        targetValue =
            if (isPressed && enabled) {
                motion.pressScaleSubtle
            } else {
                1f
            },
        animationSpec = floatAnimSpec,
    )

    // ─── Hover background ────────────────────────────────
    val hoverAlpha by animateFloatAsState(
        targetValue =
            if (isHovered && enabled && !selected) {
                0.5f
            } else {
                0f
            },
        animationSpec = fastFloatSpec,
    )

    Box(
        modifier =
            modifier
                .then(
                    if (style.itemWidth != null) {
                        Modifier.width(style.itemWidth)
                    } else {
                        Modifier.weight(1f)
                    },
                )
                .then(
                    if (style.slidingIndicator) {
                        Modifier.onPlaced { coords ->
                            centerX = coords.positionInParent().x + coords.size.width / 2f
                        }
                    } else {
                        Modifier
                    },
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    enabled = enabled,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    this.selected = selected
                    if (!enabled) disabled()
                }.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ─── Icon with indicator ─────────────────
            Box(
                contentAlignment = Alignment.Center,
            ) {
                // Indicator drawn in place. Skipped when the bar owns a shared indicator that
                // slides between items.
                if (!style.slidingIndicator) {
                    Box(
                        modifier =
                            Modifier
                                .width(indicatorWidth)
                                .height(style.indicatorHeight)
                                .graphicsLayer { alpha = indicatorAlpha }
                                .background(
                                    color = resolvedIndicator,
                                    shape = KazTheme.shapes.full,
                                ),
                    )
                }

                // Hover highlight
                if (hoverAlpha > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .width(style.indicatorWidth)
                                .height(style.indicatorHeight)
                                .graphicsLayer { alpha = hoverAlpha }
                                .background(
                                    color = colors.muted,
                                    shape = KazTheme.shapes.full,
                                ),
                    )
                }

                // Icon
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected && selectedIcon != null) {
                        selectedIcon()
                    } else {
                        icon()
                    }
                }
            }

            // ─── Label ───────────────────────────────
            if (label != null && style.showLabels) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = KazTheme.spacing.xs)
                            .offset { IntOffset(x = 0, y = labelOffset.roundToPx()) }
                            .graphicsLayer { alpha = labelAlpha },
                ) {
                    label()
                }
            }
        }
    }
}

// ─── NavigationBarItem (convenience overload) ─────────────────

/**
 * Convenience navigation bar item that accepts [ImageVector] and [String] directly.
 *
 * Automatically animates icon and label colors between active/inactive states.
 * Delegates to the content-lambda overload of [NavigationBarItem].
 *
 * @param selected Whether this item is currently selected.
 * @param onClick Callback invoked when the item is clicked.
 * @param icon [ImageVector] icon displayed in the default (unselected) state.
 * @param label String label displayed below the icon.
 * @param modifier [Modifier] applied to the item container.
 * @param selectedIcon Optional [ImageVector] icon displayed when selected. Falls back to [icon] if null.
 * @param enabled Whether the item is interactive. Defaults to true.
 * @param alwaysShowLabel Whether to always show the label or only when selected. Defaults to true.
 * @param animation [NavigationBarAnimation] style for state transitions. Defaults to [NavigationBarAnimation.Spring].
 * @param indicatorColor Color of the selection indicator pill. Defaults to [KazTheme.colors.secondary] when [Color.Unspecified].
 * @param activeColor Icon and label color when selected. Defaults to [KazTheme.colors.primary] when [Color.Unspecified].
 * @param inactiveColor Icon and label color when not selected. Defaults to [KazTheme.colors.onMuted] when [Color.Unspecified].
 */
@Composable
public fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector? = null,
    enabled: Boolean = true,
    alwaysShowLabel: Boolean = true,
    animation: NavigationBarAnimation = NavigationBarAnimation.Spring,
    indicatorColor: Color = Color.Unspecified,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
) {
    val colors = KazTheme.colors
    val motion = KazTheme.motion

    // ─── Resolve color targets ────────────────────────────
    val resolvedActive =
        if (activeColor != Color.Unspecified) {
            activeColor
        } else {
            colors.primary
        }
    val resolvedInactive =
        if (inactiveColor != Color.Unspecified) {
            inactiveColor
        } else {
            colors.onMuted
        }

    // ─── Resolve animation spec for colors ────────────────
    val colorAnimSpec: AnimationSpec<Color> = resolveAnimSpec(animation, motion)

    // ─── Animated icon + label colors ────────────────────
    val iconColor by animateColorAsState(
        targetValue = if (selected) resolvedActive else resolvedInactive,
        animationSpec = colorAnimSpec,
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) resolvedActive else resolvedInactive,
        animationSpec = colorAnimSpec,
    )

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
            )
        },
        modifier =
            modifier.semantics {
                contentDescription = label
            },
        label = {
            Text(
                text = label,
                variant = TextVariant.Small,
                color = labelColor,
            )
        },
        selectedIcon =
            if (selectedIcon != null) {
                {
                    Icon(
                        imageVector = selectedIcon,
                        contentDescription = null,
                        tint = iconColor,
                    )
                }
            } else {
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                    )
                }
            },
        enabled = enabled,
        alwaysShowLabel = alwaysShowLabel,
        animation = animation,
        indicatorColor = indicatorColor,
    )
}

// ─── Internal: Animation Spec Resolution ──────────────────────

@Composable
private fun <T> resolveAnimSpec(
    animation: NavigationBarAnimation,
    motion: com.kazox.ui.foundation.KazMotion,
): AnimationSpec<T> =
    when (animation) {
        NavigationBarAnimation.Spring -> motion.spatialDefault()
        NavigationBarAnimation.Tween -> motion.effectsDefault()
        NavigationBarAnimation.None -> snap()
    }
