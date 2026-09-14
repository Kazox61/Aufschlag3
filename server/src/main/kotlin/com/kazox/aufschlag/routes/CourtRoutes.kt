package com.kazox.aufschlag.routes

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.court.CreateCourtRequest
import com.kazox.aufschlag.api.court.ReplacePriceRulesRequest
import com.kazox.aufschlag.api.court.UpdateCourtRequest
import com.kazox.aufschlag.services.BookingService
import com.kazox.aufschlag.services.CourtService
import com.kazox.aufschlag.services.PriceRuleService
import com.kazox.aufschlag.services.SlotAvailabilityService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/** Mounted under `/clubs/{clubId}/courts` from [clubRoutes]; `{clubId}` is already resolved. */
fun Route.courtRoutes() {
    val courtService by inject<CourtService>()
    val priceRuleService by inject<PriceRuleService>()
    val slotAvailabilityService by inject<SlotAvailabilityService>()
    val bookingService by inject<BookingService>()

    route("/courts") {
        // Any authenticated user, not just members — guest bookings are a supported feature
        // so browsing courts/availability can't require
        // an existing membership.
        get {
            val (limit, cursor) = call.pageParams()
            call.respond(courtService.list(call.clubId(), limit, cursor))
        }
        get("/{courtId}/slots") {
            call.respond(slotAvailabilityService.slots(call.authenticatedUserId(), call.clubId(), call.courtId(), call.dateParam()))
        }
        withClubRole(MembershipRole.MEMBER) {
            get("/{courtId}/pricing") {
                call.respond(priceRuleService.list(call.clubId(), call.courtId()))
            }
        }
        withClubRole(MembershipRole.ADMIN) {
            // The admin day schedule: the day's bookings *with ids/owners/payment*, which the
            // public slots endpoint deliberately omits — needed to cancel/un-block/mark-paid.
            get("/{courtId}/bookings") {
                call.respond(bookingService.listForCourtDay(call.clubId(), call.courtId(), call.dateParam()))
            }
            post {
                val request = call.receive<CreateCourtRequest>()
                call.respond(HttpStatusCode.Created, courtService.create(call.clubId(), request))
            }
            put("/{courtId}") {
                val request = call.receive<UpdateCourtRequest>()
                call.respond(courtService.update(call.clubId(), call.courtId(), request))
            }
            put("/{courtId}/pricing") {
                val request = call.receive<ReplacePriceRulesRequest>()
                call.respond(priceRuleService.replace(call.clubId(), call.courtId(), request))
            }
        }
    }
}

private fun ApplicationCall.courtId(): Uuid = parameters.uuid("courtId")

/** Required `?date=YYYY-MM-DD` query parameter. [LocalDate.parse] throws IllegalArgumentException
 *  on garbage, which StatusPages would otherwise report as a 500 — it's a client error. */
private fun ApplicationCall.dateParam(): LocalDate {
    val raw = request.queryParameters["date"] ?: throw ApiException.validation("date is required")
    return runCatching { LocalDate.parse(raw) }.getOrNull() ?: throw ApiException.validation("Invalid date")
}
