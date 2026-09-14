package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.repositories.Keyset
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.or
import java.time.Instant
import kotlin.uuid.Uuid

/** `(at, id) > (cursor.at, cursor.id)` — rows strictly after [cursor] in `(at ASC, id ASC)`
 *  order; `TRUE` for the first page. Pair with [keysetOrder] on the same columns. */
internal fun keysetAfter(at: Column<Instant>, id: Column<Uuid>, cursor: Keyset?): Op<Boolean> =
    cursor?.let { (at greater it.at) or ((at eq it.at) and (id greater it.id)) } ?: Op.TRUE

internal fun keysetOrder(at: Column<Instant>, id: Column<Uuid>): Array<Pair<Column<*>, SortOrder>> =
    arrayOf(at to SortOrder.ASC, id to SortOrder.ASC)
