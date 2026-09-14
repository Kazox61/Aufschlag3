package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.security.JwtService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/** Every request body is JSON of a handful of fields; the largest legitimate payload is a
 *  membership application's free-form `applicationData`. Anything bigger is a mistake or an
 *  attempt to fill a jsonb column, and is rejected before it's read into memory. */
const val MAX_REQUEST_BODY_BYTES: Long = 64 * 1024

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(RequestBodyLimit) {
        bodyLimit { MAX_REQUEST_BODY_BYTES }
    }
}

/** Behind a reverse proxy (`TRUST_PROXY_HEADERS=true`) `call.request.origin` — and with it the
 *  per-IP rate-limit key — must come from the proxy's forwarding headers, not the proxy's own
 *  address. Off by default: on a directly exposed server these headers are client-controlled. */
fun Application.configureProxyHeaders(trustProxyHeaders: Boolean) {
    if (!trustProxyHeaders) return
    install(ForwardedHeaders)
    install(XForwardedHeaders)
}

/** Lets the admin web app (a browser SPA on its own origin) call the API.
 *  Auth is a bearer header, not cookies, so credentials stay disabled; mobile clients send no
 *  Origin header and bypass CORS entirely. Never `anyHost()` — the allowed origins come from
 *  `CORS_ALLOWED_ORIGINS` (production: the deployed admin origin). */
fun Application.configureCors(allowedOrigins: List<String>) {
    install(CORS) {
        allowedOrigins.forEach { origin ->
            val (scheme, host) = origin.split("://", limit = 2).takeIf { it.size == 2 }
                ?: error("Invalid CORS origin '$origin' — expected scheme://host[:port]")
            allowHost(host, schemes = listOf(scheme))
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}

fun Application.configureSecurity(authConfig: AuthConfig) {
    val jwtService by inject<JwtService>()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "aufschlag"
            verifier(jwtService.verifier)
            validate { credential ->
                credential.payload.subject
                    ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    ?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiError(ErrorCode.UNAUTHORIZED, "Invalid or expired token"),
                )
            }
        }
    }

    // Per-IP limit on the auth routes. Per-email backoff for login lives in LoginBackoff.
    // NOTE for deployment: behind a reverse proxy, install ForwardedHeaders/XForwardedHeaders
    // (trusted proxy only) so remoteHost is the real client IP, not the proxy's.
    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = authConfig.rateLimit, refillPeriod = authConfig.rateLimitRefill)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.code, cause.message, cause.field))
        }
        // Ktor throws this when the request body can't be deserialized. The innermost cause is
        // kotlinx.serialization's message ("Field 'email' is required…"), which names the
        // offending field and nothing else — worth passing on, it saves clients guessing.
        exception<BadRequestException> { call, cause ->
            val detail = generateSequence<Throwable>(cause) { it.cause }.last().message
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(ErrorCode.VALIDATION_FAILED, "Invalid request body: ${detail ?: "malformed JSON"}"),
            )
        }
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ApiError(ErrorCode.VALIDATION_FAILED, "Request body exceeds $MAX_REQUEST_BODY_BYTES bytes"),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(ErrorCode.INTERNAL_ERROR, "Internal server error"),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiError(ErrorCode.NOT_FOUND, "Route not found"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ApiError(ErrorCode.RATE_LIMITED, "Too many requests"))
        }
    }
}
