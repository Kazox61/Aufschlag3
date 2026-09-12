package com.kazox.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazox.ui.foundation.modifier.LocalMinTouchTarget

/**
 * KazTheme is the top-level theme composable for the KazUI design system.
 *
 * Usage:
 * ```
 * KazTheme(
 *     colors = KazPalettes.ZincLight,  // or ZincDark, SlateLight, NeutralDark, etc.
 * ) {
 *     // Your app content — access tokens via KazTheme.colors, KazTheme.typography, etc.
 *     Button(...)
 * }
 * ```
 *
 * All parameters have sensible defaults (Neutral light palette).
 */
@Composable
public fun KazTheme(
    colors: KazColors = KazPalettes.NeutralLight,
    typography: KazTypography = kazTypography(),
    spacing: KazSpacing = kazSpacing(),
    shapes: KazShapes = kazShapes(),
    motion: KazMotion = KazMotion(),
    elevation: KazElevation = KazElevation(),
    minTouchTarget: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKazColors provides colors,
        LocalContentColor provides colors.onBackground,
        LocalKazTypography provides typography,
        LocalKazSpacing provides spacing,
        LocalKazShapes provides shapes,
        LocalKazMotion provides motion,
        LocalKazElevation provides elevation,
        LocalMinTouchTarget provides minTouchTarget,
        content = content,
    )
}

/**
 * Convenience overload that applies a [KazStyle] directly.
 *
 * Usage:
 * ```
 * KazTheme(
 *     colors = KazPalettes.ZincDark,
 *     style = KazStylePreset.Nova.style,
 *     typography = kazTypography(myFont, scale = KazStylePreset.Nova.typeScale),
 * ) { ... }
 * ```
 *
 * The [style] provides shapes, spacing, and motion. You still supply
 * colors and typography separately since they depend on font/palette choice.
 */
@Composable
public fun KazTheme(
    colors: KazColors = KazPalettes.NeutralLight,
    style: KazStyle,
    typography: KazTypography = kazTypography(scale = style.typeScale),
    content: @Composable () -> Unit,
) {
    KazTheme(
        colors = colors,
        typography = typography,
        spacing = style.spacing,
        shapes = style.shapes,
        motion = style.motion,
        content = content,
    )
}

/**
 * Convenience overload that applies a [KazStylePreset] enum directly.
 *
 * The simplest way to theme your entire app:
 * ```
 * KazTheme(
 *     colors = KazPalettes.ZincDark,
 *     preset = KazStylePreset.Nova,
 * ) { ... }
 * ```
 */
@Composable
public fun KazTheme(
    colors: KazColors = KazPalettes.NeutralLight,
    preset: KazStylePreset,
    typography: KazTypography = kazTypography(scale = preset.typeScale),
    content: @Composable () -> Unit,
) {
    KazTheme(
        colors = colors,
        typography = typography,
        spacing = preset.spacing,
        shapes = preset.shapes,
        motion = preset.motion,
        content = content,
    )
}

/**
 * All-in-one overload: palette + accent + dark mode in a single call.
 *
 * The simplest way to set up a fully themed app:
 * ```
 * KazTheme(
 *     palette = KazPalette.Zinc,
 *     accent = KazAccentPreset.Blue,
 *     isDark = true,
 *     preset = KazStylePreset.Vega,
 * ) { ... }
 * ```
 */
@Composable
public fun KazTheme(
    palette: KazPalette,
    accent: KazAccentPreset = KazAccentPreset.Default,
    isDark: Boolean = false,
    preset: KazStylePreset = KazStylePreset.Default,
    typography: KazTypography =
        kazTypography(
            scale = preset.typeScale,
        ),
    content: @Composable () -> Unit,
) {
    val colors = accent.applyTo(palette.resolve(isDark), isDark)
    KazTheme(
        colors = colors,
        typography = typography,
        spacing = preset.spacing,
        shapes = preset.shapes,
        motion = preset.motion,
        content = content,
    )
}

/**
 * Access point for the current KazUI theme values.
 *
 * Usage:
 * ```
 * val primary = KazTheme.colors.primary
 * val heading = KazTheme.typography.h1
 * val padding = KazTheme.spacing.lg
 * val rounded = KazTheme.shapes.md
 * val spring = KazTheme.motion.springDefault
 * ```
 */
public object KazTheme {
    public val colors: KazColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKazColors.current

    public val typography: KazTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalKazTypography.current

    public val spacing: KazSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalKazSpacing.current

    public val shapes: KazShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalKazShapes.current

    public val motion: KazMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalKazMotion.current

    public val elevation: KazElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalKazElevation.current

    public val minTouchTarget: Dp
        @Composable
        @ReadOnlyComposable
        get() = LocalMinTouchTarget.current
}

/**
 * Returns the appropriate content (foreground) color for the given [backgroundColor].
 *
 * Matches against the current theme's color tokens to find the corresponding
 * foreground color. Falls back to [KazColors.foreground] if no match is found.
 *
 * ### Usage
 * ```
 * val bg = KazTheme.colors.primary
 * val fg = contentColorFor(bg) // → primaryForeground
 * ```
 */
@Composable
@ReadOnlyComposable
public fun contentColorFor(backgroundColor: Color): Color {
    val colors = KazTheme.colors
    // Most specific first — tinted and inverse may share values
    // with common surfaces (e.g. primaryTinted == muted in some palettes).
    // Skip Unspecified tokens to avoid false matches on unset containers.
    return when {
        backgroundColor == Color.Unspecified -> colors.onBackground

        colors.primaryTinted != Color.Unspecified &&
            backgroundColor == colors.primaryTinted -> colors.onPrimaryTinted

        colors.destructiveTinted != Color.Unspecified &&
            backgroundColor == colors.destructiveTinted -> colors.onDestructiveTinted

        colors.inverseSurface != Color.Unspecified &&
            backgroundColor == colors.inverseSurface -> colors.onInverseSurface

        backgroundColor == colors.primary -> colors.onPrimary

        backgroundColor == colors.destructive -> colors.onDestructive

        backgroundColor == colors.warning -> colors.onWarning

        backgroundColor == colors.success -> colors.onSuccess

        backgroundColor == colors.secondary -> colors.onSecondary

        backgroundColor == colors.muted -> colors.onMuted

        backgroundColor == colors.surface -> colors.onSurface

        backgroundColor == colors.background -> colors.onBackground

        else -> colors.onBackground
    }
}
