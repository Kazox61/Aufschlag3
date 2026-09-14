package com.kazox.aufschlag.routes

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.auth.DeleteAccountRequest
import com.kazox.aufschlag.services.AuthService
import com.kazox.aufschlag.services.BookingService
import com.kazox.aufschlag.services.MembershipService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.meRoutes() {
    val authService by inject<AuthService>()
    val membershipService by inject<MembershipService>()
    val bookingService by inject<BookingService>()

    authenticate("auth-jwt") {
        get("/me") {
            call.respond(authService.me(call.authenticatedUserId()))
        }
        get("/me/memberships") {
            val (limit, cursor) = call.pageParams()
            call.respond(membershipService.myMemberships(call.authenticatedUserId(), limit, cursor))
        }
        get("/me/bookings") {
            val (limit, cursor) = call.pageParams()
            call.respond(bookingService.myBookings(call.authenticatedUserId(), limit, cursor))
        }
        delete("/me") {
            val request = call.receive<DeleteAccountRequest>()
            authService.deleteAccount(call.authenticatedUserId(), request.password)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** userId (sub claim) of the verified access token; only meaningful inside authenticate("auth-jwt"). */
fun ApplicationCall.authenticatedUserId(): Uuid =
    principal<JWTPrincipal>()
        ?.payload
        ?.subject
        ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        ?: throw ApiException.unauthorized()
