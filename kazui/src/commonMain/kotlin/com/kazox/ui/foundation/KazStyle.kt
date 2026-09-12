package com.kazox.ui.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * A bundled style configuration that combines shapes, spacing, motion,
 * and typography scale into one cohesive visual identity.
 *
 * ### Usage
 *
 * **1. Use a preset enum (quickest):**
 * ```
 * KazTheme(
 *     style = KazStylePreset.Nova,
 *     colors = KazPalettes.ZincDark,
 *     typography = kazTypography(myFont, scale = KazStylePreset.Nova.typeScale),
 * ) { ... }
 * ```
 *
 * **2. Build a custom style:**
 * ```
 * val custom = KazStyle(
 *     shapes = kazShapes(radius = 12.dp),
 *     spacing = kazSpacing(base = 5.dp),
 *     motion = KazMotionPresets.playful(),
 *     typeScale = 1.1f,
 * )
 * KazTheme(style = custom, ...) { ... }
 * ```
 *
 * @param shapes Corner radius scale.
 * @param spacing Spacing multiplier scale.
 * @param motion Animation token set (springs, tweens, press scales).
 * @param typeScale Proportional typography multiplier (1.0 = default).
 */
@Immutable
public data class KazStyle(
    val shapes: KazShapes,
    val spacing: KazSpacing,
    val motion: KazMotion,
    val typeScale: Float,
)

/**
 * Type-safe named style presets that bundle shapes, spacing, motion,
 * and typography scale into cohesive visual identities.
 *
 * Each entry resolves to a [KazStyle] via the [style] property.
 *
 * | Preset  | Radius | Base | Motion  | Scale | Feel                     |
 * |---------|--------|------|---------|-------|--------------------------|
 * | Default | 10 dp  | 4 dp | default | 1.0   | Balanced, professional   |
 * | Nova    | 4 dp   | 3 dp | snappy  | 0.9   | Sharp, dense, technical  |
 * | Vega    | 20 dp  | 5 dp | playful | 1.05  | Rounded, bouncy, fun     |
 * | Aurora  | 14 dp  | 5 dp | default | 1.1   | Spacious, large, elegant |
 * | Nebula  | 0 dp   | 3 dp | minimal | 0.85  | Square, tight, brutalist |
 *
 * ```
 * // Type-safe — compiler catches typos
 * KazTheme(style = KazStylePreset.Vega) { ... }
 *
 * // Iterate all presets in a UI picker
 * KazStylePreset.entries.forEach { preset ->
 *     Button(text = preset.label, onClick = { selected = preset })
 * }
 * ```
 */
public enum class KazStylePreset(
    /** Display label for UI pickers (e.g. "Nova"). */
    public val label: String,
) {
    /** Balanced, professional. 10dp radius, 4dp spacing, default motion, 1.0x type. */
    Default("Default"),

    /** Sharp, dense, technical. 4dp radius, 3dp spacing, snappy motion, 0.9x type. */
    Nova("Nova"),

    /** Rounded, bouncy, fun. 20dp radius, 5dp spacing, playful motion, 1.05x type. */
    Vega("Vega"),

    /** Spacious, large, elegant. 14dp radius, 5dp spacing, default motion, 1.1x type. */
    Aurora("Aurora"),

    /** Square, tight, brutalist. 0dp radius, 3dp spacing, minimal motion, 0.85x type. */
    Nebula("Nebula"),
    ;

    /** The resolved [KazStyle] for this preset (cached). */
    public val style: KazStyle by lazy {
        when (this) {
            Default -> {
                KazStyle(
                    shapes = kazShapes(),
                    spacing = KazSpacingPresets.comfortable(),
                    motion = KazMotion(),
                    typeScale = 1f,
                )
            }

            Nova -> {
                KazStyle(
                    shapes = kazShapes(radius = 4.dp),
                    spacing = KazSpacingPresets.compact(),
                    motion = KazMotionPresets.snappy(),
                    typeScale = 0.9f,
                )
            }

            Vega -> {
                KazStyle(
                    shapes = kazShapes(radius = 20.dp),
                    spacing = KazSpacingPresets.spacious(),
                    motion = KazMotionPresets.playful(),
                    typeScale = 1.05f,
                )
            }

            Aurora -> {
                KazStyle(
                    shapes = kazShapes(radius = 14.dp),
                    spacing = KazSpacingPresets.spacious(),
                    motion = KazMotion(),
                    typeScale = 1.1f,
                )
            }

            Nebula -> {
                KazStyle(
                    shapes = kazShapes(radius = 0.dp),
                    spacing = KazSpacingPresets.compact(),
                    motion = KazMotionPresets.minimal(),
                    typeScale = 0.85f,
                )
            }
        }
    }

    /** Shortcut: the typography scale for this preset. */
    public val typeScale: Float get() = style.typeScale

    /** Shortcut: the shapes for this preset. */
    public val shapes: KazShapes get() = style.shapes

    /** Shortcut: the spacing for this preset. */
    public val spacing: KazSpacing get() = style.spacing

    /** Shortcut: the motion for this preset. */
    public val motion: KazMotion get() = style.motion
}
