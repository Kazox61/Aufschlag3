package com.kazox.aufschlag.routes

import com.kazox.aufschlag.api.auth.LoginRequest
import com.kazox.aufschlag.api.auth.LogoutRequest
import com.kazox.aufschlag.api.auth.PasswordResetConfirmRequest
import com.kazox.aufschlag.api.auth.PasswordResetRequest
import com.kazox.aufschlag.api.auth.RefreshRequest
import com.kazox.aufschlag.api.auth.RegisterRequest
import com.kazox.aufschlag.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val authService by inject<AuthService>()

    rateLimit(RateLimitName("auth")) {
        route("/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                call.respond(HttpStatusCode.Created, authService.register(request))
            }
            post("/login") {
                val request = call.receive<LoginRequest>()
                call.respond(authService.login(request))
            }
            post("/refresh") {
                val request = call.receive<RefreshRequest>()
                call.respond(authService.refresh(request.refreshToken))
            }
            post("/logout") {
                val request = call.receive<LogoutRequest>()
                authService.logout(request.refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }
            route("/password-reset") {
                post("/request") {
                    val request = call.receive<PasswordResetRequest>()
                    authService.requestPasswordReset(request.email)
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/confirm") {
                    val request = call.receive<PasswordResetConfirmRequest>()
                    authService.confirmPasswordReset(request.token, request.newPassword)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
