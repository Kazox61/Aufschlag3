package com.kazox.aufschlag.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.kazox.aufschlag.config.AuthConfig
import java.time.Instant
import kotlin.time.toJavaDuration
import kotlin.uuid.Uuid

class JwtService(private val config: AuthConfig) {

    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    /** Access token carries identity only (sub = userId); roles are looked up per request. */
    fun issueAccessToken(userId: Uuid, now: Instant = Instant.now()): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plus(config.accessTokenTtl.toJavaDuration()))
            .sign(algorithm)

    companion object {
        const val ISSUER = "aufschlag"
    }
}
