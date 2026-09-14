package com.kazox.aufschlag.repositories

import java.time.Instant
import kotlin.uuid.Uuid

data class UserRow(
    val id: Uuid,
    val email: String,
    val name: String,
    val emailVerifiedAt: Instant?,
    val isSuperAdmin: Boolean,
)

/** All methods must be called inside a [com.kazox.aufschlag.db.withTransaction] block. */
interface UserRepository {
    fun findById(id: Uuid): UserRow?
    fun findByEmail(email: String): UserRow?
    fun create(email: String, name: String): Uuid

    /** Cascades to auth_identities/refresh_tokens/password_reset_tokens/memberships (GDPR delete). */
    fun delete(id: Uuid)
}
