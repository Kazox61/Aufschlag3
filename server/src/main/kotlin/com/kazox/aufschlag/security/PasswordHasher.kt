package com.kazox.aufschlag.security

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/** bcrypt at cost 12 takes ~250 ms of pure CPU per call by design. [hash] and [verify] run it
 *  on [Dispatchers.Default] so a burst of logins can't monopolize the request threads.
 *
 *  bcrypt only consumes the first 72 bytes of UTF-8; the library's default strategy throws on
 *  anything longer, which a 128-char (or multi-byte) password within [RegistrationRules] would
 *  hit. Long inputs are pre-hashed with SHA-512 instead — on both sides, so hashing and
 *  verification agree; shorter passwords are passed through unchanged, so existing hashes
 *  stay valid. */
class PasswordHasher {

    suspend fun hash(password: String): String = withContext(Dispatchers.Default) { hashBlocking(password) }

    suspend fun verify(password: String, hash: String): Boolean = withContext(Dispatchers.Default) {
        BCrypt.verifyer(VERSION, LONG_PASSWORD_STRATEGY).verify(password.toCharArray(), hash.toCharArray()).verified
    }

    /**
     * Hash of a random value nobody knows. Verifying against it when no account exists
     * keeps "unknown email" and "wrong password" timing-comparable (no user enumeration
     * via response time).
     */
    val dummyHash: String = hashBlocking(Uuid.random().toString())

    private fun hashBlocking(password: String): String =
        BCrypt.with(VERSION, LONG_PASSWORD_STRATEGY).hashToString(COST, password.toCharArray())

    companion object {
        private const val COST = 12
        private val VERSION = BCrypt.Version.VERSION_2A
        private val LONG_PASSWORD_STRATEGY = LongPasswordStrategies.hashSha512(VERSION)
    }
}
