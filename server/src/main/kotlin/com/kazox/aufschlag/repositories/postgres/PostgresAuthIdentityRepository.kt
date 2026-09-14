package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.AuthIdentitiesTable
import com.kazox.aufschlag.repositories.AuthIdentityRepository
import com.kazox.aufschlag.repositories.EmailIdentity
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/** `auth_identities.subject` is plain (case-sensitive) text, so EMAIL subjects are normalized
 *  here as well as in AuthService — a direct caller can't accidentally store or look up a
 *  mixed-case variant that would never match again. */
class PostgresAuthIdentityRepository : AuthIdentityRepository {

    override fun createEmailIdentity(userId: Uuid, email: String, passwordHash: String) {
        AuthIdentitiesTable.insert {
            it[AuthIdentitiesTable.id] = Uuid.random()
            it[AuthIdentitiesTable.userId] = userId
            it[provider] = PROVIDER_EMAIL
            it[subject] = email.normalizeEmailSubject()
            it[AuthIdentitiesTable.passwordHash] = passwordHash
        }
    }

    override fun findEmailIdentity(email: String): EmailIdentity? =
        AuthIdentitiesTable.selectAll()
            .where {
                (AuthIdentitiesTable.provider eq PROVIDER_EMAIL) and
                    (AuthIdentitiesTable.subject eq email.normalizeEmailSubject())
            }
            .singleOrNull()
            ?.let { row ->
                row[AuthIdentitiesTable.passwordHash]?.let { hash ->
                    EmailIdentity(userId = row[AuthIdentitiesTable.userId], passwordHash = hash)
                }
            }

    override fun updatePassword(userId: Uuid, passwordHash: String) {
        AuthIdentitiesTable.update({
            (AuthIdentitiesTable.userId eq userId) and (AuthIdentitiesTable.provider eq PROVIDER_EMAIL)
        }) {
            it[AuthIdentitiesTable.passwordHash] = passwordHash
        }
    }

    companion object {
        const val PROVIDER_EMAIL = "EMAIL"

        private fun String.normalizeEmailSubject(): String = trim().lowercase()
    }
}
