package com.kazox.ui.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import com.kazox.ui.foundation.KazTheme

// ─── Variant ────────────────────────────────────────────────

/** List style variants. */
public enum class ListVariant {
    /** Bullet points (•). */
    Unordered,

    /** Numbered items (1. 2. 3.). */
    Ordered,

    /** No markers, just indented items. */
    None,
}

// ─── Component ──────────────────────────────────────────────

/**
 * Themed list component that renders string items with bullet, numbered, or plain markers.
 *
 * Convenience overload that accepts a list of strings. Each item is rendered with the
 * specified [textVariant]. Provides [CollectionInfo] accessibility semantics.
 *
 * ```
 * KazList(
 *     items = listOf("First item", "Second item", "Third item"),
 *     variant = ListVariant.Ordered,
 * )
 * ```
 *
 * @param items List of string items to display.
 * @param modifier [Modifier] applied to the list column.
 * @param variant [ListVariant] controlling the marker style (Unordered, Ordered, or None). Defaults to [ListVariant.Unordered].
 * @param textVariant [TextVariant] applied to each item's text. Defaults to [TextVariant.P].
 */
@Composable
public fun KazList(
    items: List<String>,
    modifier: Modifier = Modifier,
    variant: ListVariant = ListVariant.Unordered,
    textVariant: TextVariant = TextVariant.P,
) {
    Column(
        modifier =
            modifier
                .padding(start = KazTheme.spacing.md)
                .semantics {
                    collectionInfo =
                        CollectionInfo(
                            rowCount = items.size,
                            columnCount = 1,
                        )
                },
        verticalArrangement = Arrangement.spacedBy(KazTheme.spacing.xs),
    ) {
        items.forEachIndexed { index, item ->
            ListItemRow(
                index = index,
                marker = resolveMarker(variant, index),
                textVariant = textVariant,
            ) {
                Text(text = item, variant = textVariant)
            }
        }
    }
}

/**
 * Themed list component with a DSL builder for custom composable list items.
 *
 * Generic overload that accepts a [ListScope] builder for rendering arbitrary
 * composable content per item. Provides [CollectionInfo] accessibility semantics.
 *
 * ```
 * KazList(variant = ListVariant.Unordered) {
 *     ListItem { Text("Custom item with bold", style = TextStyle(fontWeight = FontWeight.Bold)) }
 *     ListItem { Row { Icon(KazIcons.Star, null); Text("Starred item") } }
 * }
 * ```
 *
 * @param modifier [Modifier] applied to the list column.
 * @param variant [ListVariant] controlling the marker style (Unordered, Ordered, or None). Defaults to [ListVariant.Unordered].
 * @param content [ListScope] DSL builder where each item is added via [ListScope.ListItem].
 */
@Composable
public fun KazList(
    modifier: Modifier = Modifier,
    variant: ListVariant = ListVariant.Unordered,
    content: @Composable ListScope.() -> Unit,
) {
    val scope = ListScopeImpl(variant)
    scope.content()

    Column(
        modifier =
            modifier
                .padding(start = KazTheme.spacing.md)
                .semantics {
                    collectionInfo =
                        CollectionInfo(
                            rowCount = scope.items.size,
                            columnCount = 1,
                        )
                },
        verticalArrangement = Arrangement.spacedBy(KazTheme.spacing.xs),
    ) {
        scope.items.forEachIndexed { index, itemContent ->
            ListItemRow(
                index = index,
                marker = resolveMarker(variant, index),
                textVariant = TextVariant.P,
            ) {
                itemContent()
            }
        }
    }
}

// ─── ListItem ───────────────────────────────────────────────

public interface ListScope {
    public fun ListItem(content: @Composable () -> Unit)
}

private class ListScopeImpl(
    private val variant: ListVariant,
) : ListScope {
    val items = mutableListOf<@Composable () -> Unit>()

    @Suppress("FunctionName")
    override fun ListItem(content: @Composable () -> Unit) {
        items.add(content)
    }
}

// ─── Internal ───────────────────────────────────────────────

@Composable
private fun ListItemRow(
    index: Int,
    marker: String,
    textVariant: TextVariant,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier.semantics {
                collectionItemInfo =
                    CollectionItemInfo(
                        rowIndex = index,
                        rowSpan = 1,
                        columnIndex = 0,
                        columnSpan = 1,
                    )
            },
        horizontalArrangement = Arrangement.spacedBy(KazTheme.spacing.sm),
    ) {
        if (marker.isNotEmpty()) {
            Text(
                text = marker,
                variant = textVariant,
                color = KazTheme.colors.onMuted,
                modifier =
                    Modifier
                        .widthIn(min = 20.dp)
                        .clearAndSetSemantics {},
                style = TextStyle.Default,
            )
        }
        content()
    }
}

private fun resolveMarker(
    variant: ListVariant,
    index: Int,
): String =
    when (variant) {
        ListVariant.Unordered -> "•"
        ListVariant.Ordered -> "${index + 1}."
        ListVariant.None -> ""
    }
