package com.kazox.aufschlag.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // Ops endpoint, outside the versioned API (load balancer / uptime checks)
        healthRoutes()

        route("/v1") {
            authRoutes()
            meRoutes()
            clubRoutes()
        }
    }
}
