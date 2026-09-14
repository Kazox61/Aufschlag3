package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.court.PriceRuleRequest
import com.kazox.aufschlag.api.court.PriceRuleResponse
import com.kazox.aufschlag.api.court.ReplacePriceRulesRequest
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.pricing.PriceRule
import com.kazox.aufschlag.pricing.hasOverlap
import com.kazox.aufschlag.pricing.isoDayNumberOf
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.CourtRepository
import com.kazox.aufschlag.repositories.NewPriceRule
import com.kazox.aufschlag.repositories.PriceRuleRepository
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.Uuid

/** ADMIN+ for replace, MEMBER+ for reads — enforced by
 *  [com.kazox.aufschlag.routes.withClubRole] at the route layer. */
class PriceRuleService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val courts: CourtRepository,
    private val priceRules: PriceRuleRepository,
    private val entitlements: EntitlementService,
) {
    /** Wholesale replace: validated as one internally consistent set before anything is
     *  written, then swapped in in a single transaction (PLANNING.md "Booking pricing"). */
    suspend fun replace(clubId: Uuid, courtId: Uuid, request: ReplacePriceRulesRequest): List<PriceRuleResponse> {
        request.rules.forEach { validate(it) }
        val engineRules = request.rules.map { it.toEngineRule() }
        if (hasOverlap(engineRules)) {
            throw ApiException.validation(
                "Price rules overlap: two rules share a day, an overlapping time range, and an overlapping validity window",
            )
        }

        return withTransaction(db) {
            val club = clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            entitlements.requireWritable(club)
            courts.findByIdAndClub(courtId, clubId) ?: throw ApiException.notFound("Court not found")
            priceRules.lockForReplace(courtId)
            priceRules.replaceAll(courtId, request.rules.map { it.toNewPriceRule() })
            priceRules.findByCourt(courtId).map { it.toResponse() }
        }
    }

    suspend fun list(clubId: Uuid, courtId: Uuid): List<PriceRuleResponse> =
        withTransaction(db) {
            courts.findByIdAndClub(courtId, clubId) ?: throw ApiException.notFound("Court not found")
            priceRules.findByCourt(courtId).map { it.toResponse() }
        }

    private fun validate(rule: PriceRuleRequest) {
        if (rule.daysOfWeek.isEmpty()) throw ApiException.validation("daysOfWeek must not be empty")
        if (rule.endTime <= rule.startTime) throw ApiException.validation("endTime must be after startTime")
        if (rule.priceCents < 0) throw ApiException.validation("priceCents must be >= 0")
        val from = rule.validFrom
        val to = rule.validTo
        if (from != null && to != null && to < from) {
            throw ApiException.validation("validTo must not be before validFrom")
        }
    }

    private fun PriceRuleRequest.toEngineRule() = PriceRule(
        daysOfWeek = daysOfWeek,
        startTime = startTime,
        endTime = endTime,
        validFrom = validFrom,
        validTo = validTo,
        priceCents = priceCents,
    )

    private fun PriceRuleRequest.toNewPriceRule() = NewPriceRule(
        daysOfWeekJson = encodeDaysOfWeekJson(daysOfWeek.map(::isoDayNumberOf)),
        startTime = startTime.toJavaLocalTime(),
        endTime = endTime.toJavaLocalTime(),
        validFrom = validFrom?.toJavaLocalDate(),
        validTo = validTo?.toJavaLocalDate(),
        priceCents = priceCents,
    )
}
