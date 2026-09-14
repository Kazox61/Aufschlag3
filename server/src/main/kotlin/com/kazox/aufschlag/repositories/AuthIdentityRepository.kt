package com.kazox.aufschlag.repositories

import kotlin.uuid.Uuid

data class EmailIdentity(
    val userId: Uuid,
    val passwordHash: String,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface AuthIdentityRepository {
    fun createEmailIdentity(userId: Uuid, email: String, passwordHash: String)

    /** The EMAIL identity whose subject is [email] (stored trimmed + lowercased). */
    fun findEmailIdentity(email: String): EmailIdentity?

    fun updatePassword(userId: Uuid, passwordHash: String)
}
