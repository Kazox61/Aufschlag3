package com.kazox.aufschlag.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Opaque tokens (refresh, password reset): 256-bit random, stored only as SHA-256. */
object Tokens {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .toHexString()
}
