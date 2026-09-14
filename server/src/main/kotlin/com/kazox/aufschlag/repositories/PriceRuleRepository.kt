package com.kazox.aufschlag.repositories

import java.time.LocalDate
import java.time.LocalTime
import kotlin.uuid.Uuid

data class PriceRuleRow(
    val id: Uuid,
    val courtId: Uuid,
    /** JSON array of ISO day numbers (1=Mon..7=Sun); see [com.kazox.aufschlag.db.Tables]. */
    val daysOfWeekJson: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val validFrom: LocalDate?,
    val validTo: LocalDate?,
    val priceCents: Int,
)

data class NewPriceRule(
    val daysOfWeekJson: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val validFrom: LocalDate?,
    val validTo: LocalDate?,
    val priceCents: Int,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface PriceRuleRepository {
    fun findByCourt(courtId: Uuid): List<PriceRuleRow>

    /** Deletes the court's existing rules and inserts [rules] — callers wrap this in one
     *  transaction so concurrent admin edits are last-write-wins of an internally consistent
     *  set, never a partial-overlap merge (PLANNING.md "Booking pricing"). Callers must hold
     *  [lockForReplace] for [courtId] first: a plain delete-then-insert takes no lock when the
     *  court currently has zero rules (or once the delete has removed all matching rows), so two
     *  concurrent replaces could otherwise both insert, leaving an overlapping set behind. */
    fun replaceAll(courtId: Uuid, rules: List<NewPriceRule>)

    /** `pg_advisory_xact_lock` scoped to [courtId] — held for the rest of the current
     *  transaction, serializing concurrent [replaceAll] calls for the same court. */
    fun lockForReplace(courtId: Uuid)
}
