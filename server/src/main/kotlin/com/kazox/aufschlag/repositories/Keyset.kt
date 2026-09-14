package com.kazox.aufschlag.repositories

import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Keyset-pagination position for lists ordered `(at ASC, id ASC)`: the next page is every row
 * strictly after this one. Ids are random UUIDv4s, so ordering by id alone would be random —
 * the timestamp (`created_at`, or `starts_at` for bookings) supplies the meaningful order and
 * the id breaks ties. [at] is compared at Postgres' microsecond precision by [Keyset.encode].
 */
data class Keyset(val at: Instant, val id: Uuid)
