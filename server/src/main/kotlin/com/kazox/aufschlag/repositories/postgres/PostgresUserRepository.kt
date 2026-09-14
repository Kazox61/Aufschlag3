package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.UsersTable
import com.kazox.aufschlag.repositories.UserRepository
import com.kazox.aufschlag.repositories.UserRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class PostgresUserRepository : UserRepository {

    override fun findById(id: Uuid): UserRow? =
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUserRow()

    // email is citext, so this lookup is case-insensitive
    override fun findByEmail(email: String): UserRow? =
        UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toUserRow()

    override fun create(email: String, name: String): Uuid {
        val id = Uuid.random()
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[UsersTable.name] = name
        }
        return id
    }

    override fun delete(id: Uuid) {
        UsersTable.deleteWhere { UsersTable.id eq id }
    }

    private fun ResultRow.toUserRow() = UserRow(
        id = this[UsersTable.id],
        email = this[UsersTable.email],
        name = this[UsersTable.name],
        emailVerifiedAt = this[UsersTable.emailVerifiedAt],
        isSuperAdmin = this[UsersTable.isSuperAdmin],
    )
}
