package com.kazox.aufschlag.api.court

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class PriceRuleRequest(
    val daysOfWeek: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val priceCents: Int,
)

@Serializable
data class PriceRuleResponse(
    val id: String,
    val daysOfWeek: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val priceCents: Int,
)

/** Wholesale replace: PUT sends the court's complete rule set in one request — this also
 *  makes concurrent admin edits safe (last write wins with an internally consistent set,
 *  never a partial-overlap race). */
@Serializable
data class ReplacePriceRulesRequest(
    val rules: List<PriceRuleRequest>,
)
