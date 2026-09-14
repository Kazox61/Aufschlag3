package com.kazox.aufschlag.security

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlin.uuid.Uuid

class PasswordHasher {

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray()).verified

    /**
     * Hash of a random value nobody knows. Verifying against it when no account exists
     * keeps "unknown email" and "wrong password" timing-comparable (no user enumeration
     * via response time).
     */
    val dummyHash: String = hash(Uuid.random().toString())

    companion object {
        private const val COST = 12
    }
}
