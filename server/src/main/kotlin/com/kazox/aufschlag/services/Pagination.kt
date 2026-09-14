package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.repositories.Keyset
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

internal const val MAX_PAGE_SIZE = 50

/** The `?limit=` parameter, clamped to what a page may hold. */
internal fun pageSizeOf(limit: Int): Int = limit.coerceIn(1, MAX_PAGE_SIZE)

/** Opaque wire form `<epochMicros>:<uuid>` — micros because timestamptz has microsecond
 *  precision; a millisecond cursor could skip or repeat rows created in the same millisecond. */
internal fun Keyset.encode(): String = "${ChronoUnit.MICROS.between(Instant.EPOCH, at)}:$id"

internal fun parseKeysetCursor(cursor: String?): Keyset? {
    if (cursor == null) return null
    val invalid = { ApiException.validation("Invalid cursor") }
    val (microsPart, idPart) = cursor.split(":", limit = 2).takeIf { it.size == 2 } ?: throw invalid()
    val micros = microsPart.toLongOrNull() ?: throw invalid()
    val id = runCatching { Uuid.parse(idPart) }.getOrNull() ?: throw invalid()
    return Keyset(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id)
}

/** Turns a `pageSize + 1` fetch into a [Page]: the extra row only signals that a next page
 *  exists, and the cursor is the keyset of the last row actually returned. */
internal fun <Row, Dto> List<Row>.toPage(pageSize: Int, keysetOf: (Row) -> Keyset, toDto: (Row) -> Dto): Page<Dto> {
    val page = take(pageSize)
    return Page(
        items = page.map(toDto),
        nextCursor = if (size > pageSize) keysetOf(page.last()).encode() else null,
    )
}
