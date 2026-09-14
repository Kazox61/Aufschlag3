package com.kazox.aufschlag.routes

import com.kazox.aufschlag.services.HealthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRoutes() {
    val healthService by inject<HealthService>()

    get("/health") {
        if (healthService.isDatabaseHealthy()) {
            call.respond(HealthResponse("ok"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("degraded"))
        }
    }
}
