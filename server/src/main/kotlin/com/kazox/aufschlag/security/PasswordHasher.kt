package com.kazox.aufschlag.security

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/** bcrypt at cost 12 takes ~250 ms of pure CPU per call by design. [hash] and [verify] run it
 *  on [Dispatchers.Default] so a burst of logins can't monopolize the request threads. */
class PasswordHasher {

    suspend fun hash(password: String): String = withContext(Dispatchers.Default) { hashBlocking(password) }

    suspend fun verify(password: String, hash: String): Boolean = withContext(Dispatchers.Default) {
        BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray()).verified
    }

    /**
     * Hash of a random value nobody knows. Verifying against it when no account exists
     * keeps "unknown email" and "wrong password" timing-comparable (no user enumeration
     * via response time).
     */
    val dummyHash: String = hashBlocking(Uuid.random().toString())

    private fun hashBlocking(password: String): String =
        BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    companion object {
        private const val COST = 12
    }
}
