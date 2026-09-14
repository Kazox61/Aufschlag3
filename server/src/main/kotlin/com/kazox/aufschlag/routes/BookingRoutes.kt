package com.kazox.aufschlag.routes

import com.kazox.aufschlag.api.booking.CreateBlockRequest
import com.kazox.aufschlag.api.booking.CreateBookingRequest
import com.kazox.aufschlag.api.booking.UpdateBookingPaymentRequest
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.services.BookingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/** Mounted under `/clubs/{clubId}` from [clubRoutes]; `{clubId}` is already resolved. */
fun Route.bookingRoutes() {
    val bookingService by inject<BookingService>()

    route("/bookings") {
        // Any authenticated user, not just members — guest bookings are a supported feature
        // so this deliberately isn't behind withClubRole.
        post {
            val request = call.receive<CreateBookingRequest>()
            call.respond(HttpStatusCode.Created, bookingService.create(call.authenticatedUserId(), call.clubId(), request))
        }
        // Own booking for anyone; any booking (incl. un-blocking) for an ADMIN — BookingService
        // branches on the caller's role itself, since that's a behavior split, not a bare
        // minimum-role gate withClubRole is built for.
        delete("/{bookingId}") {
            bookingService.cancel(call.authenticatedUserId(), call.clubId(), call.bookingId())
            call.respond(HttpStatusCode.NoContent)
        }
        withClubRole(MembershipRole.ADMIN) {
            put("/{bookingId}/payment") {
                val request = call.receive<UpdateBookingPaymentRequest>()
                call.respond(bookingService.updatePayment(call.clubId(), call.bookingId(), request.paymentStatus))
            }
        }
    }
    withClubRole(MembershipRole.ADMIN) {
        post("/blocks") {
            val request = call.receive<CreateBlockRequest>()
            call.respond(HttpStatusCode.Created, bookingService.createBlock(call.clubId(), request))
        }
    }
}

private fun ApplicationCall.bookingId(): Uuid = parameters.uuid("bookingId")
