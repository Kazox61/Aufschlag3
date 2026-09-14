package com.kazox.aufschlag.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val auth: AuthConfig,
    val mail: MailConfig,
    /** Origins (`scheme://host[:port]`) allowed to call the API from a browser — the admin
     *  web app. Mobile clients don't send an Origin header and are
     *  unaffected. */
    val corsAllowedOrigins: List<String>,
    /** Trust `X-Forwarded-*`/`Forwarded` headers for the client IP (rate limiting) and scheme.
     *  Only set behind a reverse proxy that strips these headers from incoming requests —
     *  trusting them on a directly exposed server lets any client spoof its IP. */
    val trustProxyHeaders: Boolean = false,
) {
    companion object {
        /** The admin web app's dev server (`:app:webApp:wasmJsBrowserDevelopmentRun`); the Ktor
         *  server itself owns 8080. */
        val DEFAULT_CORS_ORIGINS = listOf("http://localhost:8081", "http://127.0.0.1:8081")

        /** Defaults match docker-compose.yml so a plain `./gradlew :server:run` works in dev. */
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig = AppConfig(
            port = env["PORT"]?.let {
                it.toIntOrNull()?.takeIf { port -> port in 1..65535 }
                    ?: error("PORT must be a number in 1..65535, was '$it'")
            } ?: 8080,
            database = DatabaseConfig(
                url = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/aufschlag",
                username = env["DATABASE_USER"] ?: "aufschlag",
                password = env["DATABASE_PASSWORD"] ?: "aufschlag",
            ),
            auth = AuthConfig(
                // No fallback on purpose: a silently-used default secret would make every
                // access token forgeable. The :server:run Gradle task supplies a dev value.
                jwtSecret = env["JWT_SECRET"]
                    ?: error("JWT_SECRET is not set (min $MIN_JWT_SECRET_LENGTH chars); refusing to start"),
            ),
            mail = MailConfig(
                apiKey = env["MAIL_API_KEY"],
                from = env["MAIL_FROM"] ?: "Aufschlag <noreply@localhost>",
            ),
            corsAllowedOrigins = env["CORS_ALLOWED_ORIGINS"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: DEFAULT_CORS_ORIGINS,
            trustProxyHeaders = env["TRUST_PROXY_HEADERS"]?.let {
                it.toBooleanStrictOrNull() ?: error("TRUST_PROXY_HEADERS must be 'true' or 'false', was '$it'")
            } ?: false,
        )
    }
}

data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
)

const val MIN_JWT_SECRET_LENGTH = 32

data class AuthConfig(
    val jwtSecret: String,
    val accessTokenTtl: Duration = 15.minutes,
    val refreshTokenTtl: Duration = 60.days,
    /** Re-presenting a refresh token rotated less than this ago returns the same
     *  replacement pair (lost-response client retry) instead of killing the family. */
    val graceWindow: Duration = 30.seconds,
    val resetTokenTtl: Duration = 30.minutes,
    /** Per-IP request limit on the auth routes per [rateLimitRefill]. */
    val rateLimit: Int = 20,
    val rateLimitRefill: Duration = 60.seconds,
) {
    init {
        require(jwtSecret.length >= MIN_JWT_SECRET_LENGTH) {
            "JWT_SECRET must be at least $MIN_JWT_SECRET_LENGTH chars (HS256 key strength)"
        }
    }
}

data class MailConfig(
    /** Resend API key; when null, mails are logged instead of sent (dev). */
    val apiKey: String?,
    val from: String,
)
