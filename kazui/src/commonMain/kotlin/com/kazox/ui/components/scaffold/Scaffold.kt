package com.kazox.ui.components.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.kazox.ui.foundation.LocalContentColor
import com.kazox.ui.foundation.KazTheme

// ─── Subcompose Slots ──────────────────────────────────────

@Immutable
private enum class ScaffoldSlot {
    TopBar,
    BottomBar,
    BottomBarScrim,
    Fab,
    SnackbarHost,
    ToastHost,
    Content,
}

// ─── FAB Position ──────────────────────────────────────────

@kotlin.jvm.JvmInline
public value class FabPosition internal constructor(
    @Suppress("unused") private val value: Int,
) {
    public companion object {
        /** Position FAB at the bottom of the screen at the start, above the bottom bar. */
        public val Start: FabPosition = FabPosition(0)

        /** Position FAB at the bottom of the screen in the center, above the bottom bar. */
        public val Center: FabPosition = FabPosition(1)

        /** Position FAB at the bottom of the screen at the end, above the bottom bar. */
        public val End: FabPosition = FabPosition(2)

        /** Position FAB at the bottom of the screen at the end, overlaying the bottom bar. */
        public val EndOverlay: FabPosition = FabPosition(3)
    }

    override fun toString(): String =
        when (this) {
            Start -> "FabPosition.Start"
            Center -> "FabPosition.Center"
            End -> "FabPosition.End"
            else -> "FabPosition.EndOverlay"
        }
}

/** How far the overlay scrim's fade extends above the bottom bar. */
private val ScrimFadeHeight = 40.dp

// ─── Content Window Insets ──────────────────────────────────

@Immutable
public data class ScaffoldWindowInsets(
    public val left: Dp = 0.dp,
    public val top: Dp = 0.dp,
    public val right: Dp = 0.dp,
    public val bottom: Dp = 0.dp,
)

// ─── Component ─────────────────────────────────────────────

/**
 * Layout scaffold that manages top bar, bottom bar, FAB, snackbar, toast, and body content slots.
 *
 * Uses [SubcomposeLayout] to measure bars first and provide correct [PaddingValues] to the body.
 * The system safe area ([WindowInsets.Companion.safeDrawing]) is consumed by the bar slots: the
 * top bar clears the status bar and the bottom bar rides above the home indicator, while the
 * scaffold's background paints edge to edge behind them — so callers must not apply
 * safe-area padding around the scaffold itself. Toasts are rendered as the highest layer,
 * always on top of all other content.
 * Provides [LocalContentColor] from [contentColor] to all children.
 *
 * @param modifier [Modifier] applied to the scaffold root.
 * @param topBar Composable slot for the top app bar. Defaults to empty.
 * @param bottomBar Composable slot for the bottom navigation bar. Defaults to empty.
 * @param floatingActionButton Composable slot for the FAB. Defaults to empty.
 * @param floatingActionButtonPosition [FabPosition] controlling FAB placement. Defaults to [FabPosition.End].
 * @param snackbarHost Composable slot for the snackbar host. Defaults to empty.
 * @param toastHost Composable slot for the toast host overlay. Defaults to empty.
 * @param containerColor Background color of the scaffold. Defaults to [KazTheme.colors.background].
 * @param contentColor Foreground content color provided via [LocalContentColor]. Defaults to [KazTheme.colors.onBackground].
 * @param contentWindowInsets [ScaffoldWindowInsets] for additional padding around content, on
 *   top of the system safe area the scaffold already applies. Defaults to zero insets.
 * @param overlayBottomBar When true the body extends *underneath* [bottomBar] instead of
 *   stopping above it, so scrolling content passes behind a floating bar — the usual treatment
 *   for [com.kazox.ui.components.navigationbar.NavigationBarVariant.Floating]. The body runs all
 *   the way to the physical bottom edge, so content stays visible below the bar, in the safe
 *   area. The body is then responsible for keeping its own last item reachable, by feeding the
 *   reported bottom [PaddingValues] (which include the bar and the safe area) into its scroll
 *   container's `contentPadding` (see [content]). Defaults to false, which reserves space for
 *   the bar.
 * @param overlayBottomBarScrim Fades content out just above [bottomBar] instead of letting it
 *   pass behind visibly. Off by default: seeing content continue under the bar is the whole
 *   point of [overlayBottomBar], and the scrim hides it — with it on, a list looks like it
 *   simply stops at the bar. Turn it on only when content showing through the gaps around a
 *   floating bar is genuinely distracting. Ignored when [overlayBottomBar] is false.
 * @param content Main body. Unless [overlayBottomBar] is set, the slot is **already inset** for
 *   the top and bottom bars, so content starts below [topBar] and ends above [bottomBar] with no
 *   work from the caller — applying the [PaddingValues] with `Modifier.padding(...)` would
 *   double-count the bars and leave a large blank gap. This is deliberately unlike Material3's
 *   Scaffold, where content fills the whole area and the caller must apply the padding.
 *
 *   The [PaddingValues] passed in always *describes* the bar sizes. Its bottom value is what a
 *   scrolling body needs when [overlayBottomBar] is on:
 *   ```
 *   Scaffold(bottomBar = bottomBar, overlayBottomBar = true) { padding ->
 *       LazyColumn(
 *           contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
 *       ) { /* rows scroll behind the bar; the last one still clears it */ }
 *   }
 *   ```
 */
@Composable
public fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    snackbarHost: @Composable () -> Unit = {},
    toastHost: @Composable () -> Unit = {},
    containerColor: Color = KazTheme.colors.background,
    contentColor: Color = KazTheme.colors.onBackground,
    contentWindowInsets: ScaffoldWindowInsets = ScaffoldWindowInsets(),
    overlayBottomBar: Boolean = false,
    overlayBottomBarScrim: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val fabSpacing = KazTheme.spacing.md

    // System safe area (status bar, home indicator, display corners). Read once here — the
    // measure block below is not a composable context.
    val safeDrawingInsets = WindowInsets.safeDrawing

    // Smart PaddingValues: remembered once, backing state updated during
    // measurement (before body subcomposition) to avoid recomposition.
    val contentPadding =
        remember {
            MutableScaffoldPaddingValues(PaddingValues(0.dp))
        }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        SubcomposeLayout(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(containerColor),
        ) { constraints ->
            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val fabPadding = fabSpacing.roundToPx()

            val insetLeft = contentWindowInsets.left.roundToPx()
            val insetTop = contentWindowInsets.top.roundToPx()
            val insetRight = contentWindowInsets.right.roundToPx()
            val insetBottom = contentWindowInsets.bottom.roundToPx()
            val safeTop = safeDrawingInsets.getTop(this)
            val safeBottom = safeDrawingInsets.getBottom(this)

            // The bar slots consume the safe area: the top bar's content is pushed below the
            // status bar, the bottom bar rides above the home indicator, while the scaffold's
            // own background paints edge to edge behind both. With [overlayBottomBar] the body
            // ignores the bottom safe inset so content stays visible below the bar.
            val barTopInset = insetTop + safeTop

            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            // ── Measure top bar ────────────────────────────────
            val topBarPlaceables =
                subcompose(ScaffoldSlot.TopBar, topBar)
                    .map { it.measure(looseConstraints) }
            val topBarHeight = topBarPlaceables.maxOfOrNull { it.height } ?: 0

            // ── Measure bottom bar ─────────────────────────────
            val bottomBarPlaceables =
                subcompose(ScaffoldSlot.BottomBar, bottomBar)
                    .map { it.measure(looseConstraints) }
            val bottomBarHeight = bottomBarPlaceables.maxOfOrNull { it.height } ?: 0
            val isBottomBarEmpty = bottomBarHeight == 0
            // Total vertical footprint of the bar, including the safe area it is lifted by.
            val bottomBarExtent = bottomBarHeight + safeBottom

            // ── Measure FAB ────────────────────────────────────
            val fabPlaceables =
                subcompose(ScaffoldSlot.Fab, floatingActionButton)
                    .map {
                        it.measure(
                            looseConstraints.offset(
                                horizontal = -(insetLeft + insetRight),
                                vertical = -(insetBottom + safeBottom),
                            ),
                        )
                    }
            val fabWidth = fabPlaceables.maxOfOrNull { it.width } ?: 0
            val fabHeight = fabPlaceables.maxOfOrNull { it.height } ?: 0
            val isFabEmpty = fabWidth == 0 && fabHeight == 0

            // ── FAB placement ──────────────────────────────────
            val fabLeftOffset =
                if (!isFabEmpty) {
                    when (floatingActionButtonPosition) {
                        FabPosition.Start -> {
                            if (layoutDirection == LayoutDirection.Ltr) {
                                fabPadding + insetLeft
                            } else {
                                layoutWidth - fabPadding - fabWidth - insetRight
                            }
                        }

                        FabPosition.Center -> {
                            (layoutWidth - fabWidth + insetLeft - insetRight) / 2
                        }

                        FabPosition.End,
                        FabPosition.EndOverlay,
                        -> {
                            if (layoutDirection == LayoutDirection.Ltr) {
                                layoutWidth - fabPadding - fabWidth - insetRight
                            } else {
                                fabPadding + insetLeft
                            }
                        }

                        else -> {
                            (layoutWidth - fabWidth + insetLeft - insetRight) / 2
                        }
                    }
                } else {
                    0
                }

            val fabOffsetFromBottom =
                if (!isFabEmpty) {
                    if (isBottomBarEmpty || floatingActionButtonPosition == FabPosition.EndOverlay) {
                        fabHeight + fabPadding + insetBottom + safeBottom
                    } else {
                        bottomBarExtent + fabHeight + fabPadding
                    }
                } else {
                    0
                }

            // ── Measure snackbar host ──────────────────────────
            val snackbarPlaceables =
                subcompose(ScaffoldSlot.SnackbarHost, snackbarHost)
                    .map { it.measure(looseConstraints) }
            val snackbarWidth = snackbarPlaceables.maxOfOrNull { it.width } ?: 0
            val snackbarHeight = snackbarPlaceables.maxOfOrNull { it.height } ?: 0

            val snackbarOffsetFromBottom =
                if (snackbarHeight != 0) {
                    snackbarHeight +
                        (
                            if (fabOffsetFromBottom > 0) {
                                fabOffsetFromBottom
                            } else if (!isBottomBarEmpty) {
                                bottomBarExtent
                            } else {
                                insetBottom + safeBottom
                            }
                        )
                } else {
                    0
                }

            // ── Measure toast host (full-screen overlay) ────────
            val toastPlaceables =
                subcompose(ScaffoldSlot.ToastHost, toastHost)
                    .map { it.measure(looseConstraints) }

            // ── Update content padding before measuring body ───
            val totalTopPadding = topBarHeight + barTopInset
            // Everything the body must clear at the bottom when it is not overlaying: the
            // bar's own height, the safe area the bar is lifted by, and the caller's inset.
            val totalBottomPadding = bottomBarExtent + insetBottom

            contentPadding.paddingHolder =
                PaddingValues(
                    start = contentWindowInsets.left,
                    top = totalTopPadding.toDp(),
                    end = contentWindowInsets.right,
                    bottom = totalBottomPadding.toDp(),
                )

            // ── Measure content ────────────────────────────────
            // When overlaying, the body keeps only the caller's inset and runs all the way to
            // the physical bottom edge, so it renders behind the bar — and visibly below it —
            // rather than above it.
            val contentBottomInset =
                if (overlayBottomBar) insetBottom else totalBottomPadding
            val contentConstraints =
                looseConstraints.offset(
                    horizontal = -(insetLeft + insetRight),
                    vertical = -(totalTopPadding + contentBottomInset),
                )
            val contentPlaceables =
                subcompose(ScaffoldSlot.Content) {
                    content(contentPadding)
                }.map { it.measure(contentConstraints) }

            // ── Measure bottom-bar scrim ───────────────────────
            // The fade runs out *above* the bar and is fully opaque by the time it reaches it,
            // so the gaps around a floating capsule never show half-covered text. A gradient
            // only as tall as the bar would still be ~50% transparent at its midline, which is
            // exactly where a detached action button leaves a gap.
            val scrimFade = ScrimFadeHeight.roundToPx()
            val scrimHeight = bottomBarExtent + scrimFade
            val scrimPlaceables =
                if (overlayBottomBar && overlayBottomBarScrim && !isBottomBarEmpty) {
                    subcompose(ScaffoldSlot.BottomBarScrim) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(scrimHeight.toDp())
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        (scrimFade.toFloat() / scrimHeight) to containerColor,
                                        1f to containerColor,
                                    ),
                                ),
                        )
                    }.map { it.measure(looseConstraints) }
                } else {
                    emptyList()
                }

            // ── Place everything (order = drawing order) ───────
            layout(layoutWidth, layoutHeight) {
                // Body first (lowest layer)
                contentPlaceables.forEach {
                    it.placeRelative(insetLeft, totalTopPadding)
                }
                // Top bar over content, its content pushed below the status bar
                topBarPlaceables.forEach { it.placeRelative(0, barTopInset) }
                // Scrim over content but under the bar, so only the gaps around a floating bar
                // show the fade.
                scrimPlaceables.forEach {
                    it.placeRelative(0, layoutHeight - scrimHeight)
                }
                // Bottom bar pinned to the bottom, lifted by the safe area
                bottomBarPlaceables.forEach {
                    it.placeRelative(0, layoutHeight - bottomBarExtent)
                }
                // FAB above bottom bar (respects RTL via explicit offset)
                if (!isFabEmpty) {
                    fabPlaceables.forEach {
                        it.place(fabLeftOffset, layoutHeight - fabOffsetFromBottom)
                    }
                }
                // Snackbar centered, above bottom bar / FAB
                snackbarPlaceables.forEach {
                    val snackbarX =
                        (layoutWidth - snackbarWidth + insetLeft - insetRight) / 2
                    it.placeRelative(snackbarX, layoutHeight - snackbarOffsetFromBottom)
                }
                // Toast overlay on top of everything (highest layer)
                toastPlaceables.forEach { it.placeRelative(0, 0) }
            }
        }
    }
}

// ─── Smart PaddingValues ──────────────────────────────────

private class MutableScaffoldPaddingValues(
    initialPadding: PaddingValues,
) : PaddingValues {
    var paddingHolder by mutableStateOf(initialPadding)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = paddingHolder.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = paddingHolder.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = paddingHolder.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = paddingHolder.calculateBottomPadding()
}
