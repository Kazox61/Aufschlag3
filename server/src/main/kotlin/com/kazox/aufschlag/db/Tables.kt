package com.kazox.aufschlag.db

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp

/** No `exposed-json` module dependency: jsonb columns are read/written as plain strings
 *  (the app encodes/decodes via kotlinx.serialization) and only need the DDL type name to
 *  differ from a real varchar for `::jsonb` casts Postgres performs on statement parameters. */
private class JsonbColumnType : StringColumnType() {
    override fun sqlType(): String = "jsonb"
}

private fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())

// Schema is owned by Flyway (see resources/db/migration); these objects only map
// the columns the application reads/writes. created_at/updated_at stay DB-managed.

object UsersTable : Table("users") {
    val id = uuid("id")
    val email = text("email") // citext in the DB; JDBC reads/writes it as text
    val name = text("name")
    val emailVerifiedAt = timestamp("email_verified_at").nullable()
    val isSuperAdmin = bool("is_super_admin").default(false)
    override val primaryKey = PrimaryKey(id)
}

object AuthIdentitiesTable : Table("auth_identities") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val provider = text("provider") // EMAIL | GOOGLE | APPLE
    val subject = text("subject")
    val passwordHash = text("password_hash").nullable()
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val familyId = uuid("family_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val replacedById = uuid("replaced_by_id").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PasswordResetTokensTable : Table("password_reset_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ClubsTable : Table("clubs") {
    val id = uuid("id")
    val name = text("name")
    val slug = text("slug")
    val plan = text("plan").default("FREE_BETA")
    val status = text("status").default("TRIAL") // TRIAL | ACTIVE | SUSPENDED | ARCHIVED
    val timezone = text("timezone")
    val settings = jsonb("settings") // serialized ClubSettings
    val billingRef = text("billing_ref").nullable()
    val address = text("address").nullable()
    val contactEmail = text("contact_email").nullable()
    val phone = text("phone").nullable()
    val website = text("website").nullable()
    val logoUrl = text("logo_url").nullable()
    override val primaryKey = PrimaryKey(id)
}

object MembershipsTable : Table("memberships") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val clubId = uuid("club_id")
    val role = text("role") // OWNER | ADMIN | MEMBER
    val status = text("status") // PENDING | ACTIVE | PAUSED | SUSPENDED | ENDED
    val applicationData = jsonb("application_data").nullable()
    override val primaryKey = PrimaryKey(id)
}

object CourtsTable : Table("courts") {
    val id = uuid("id")
    val clubId = uuid("club_id")
    val name = text("name")
    val surface = text("surface") // CLAY | HARD | GRASS | CARPET | ARTIFICIAL_TURF
    val indoor = bool("indoor").default(false)
    val active = bool("active").default(true)
    val slotMinutes = integer("slot_minutes").default(60)
    val defaultPriceCents = integer("default_price_cents").default(0)
    val memberDiscountPct = integer("member_discount_pct").default(100)
    val pausedDiscountPct = integer("paused_discount_pct").default(0)
    override val primaryKey = PrimaryKey(id)
}

object PriceRulesTable : Table("price_rules") {
    val id = uuid("id")
    val courtId = uuid("court_id")
    val daysOfWeek = jsonb("days_of_week") // JSON array of ISO day numbers, 1=Mon..7=Sun
    val startTime = time("start_time")
    val endTime = time("end_time")
    val validFrom = date("valid_from").nullable()
    val validTo = date("valid_to").nullable()
    val priceCents = integer("price_cents")
    override val primaryKey = PrimaryKey(id)
}

object BookingsTable : Table("bookings") {
    val id = uuid("id")
    val clubId = uuid("club_id")
    val courtId = uuid("court_id")
    val userId = uuid("user_id").nullable()
    val startsAt = timestamp("starts_at")
    val endsAt = timestamp("ends_at")
    val status = text("status") // ACTIVE | CANCELLED | BLOCKED
    val note = text("note").nullable()
    val basePriceCents = integer("base_price_cents")
    val discountPct = integer("discount_pct")
    val finalPriceCents = integer("final_price_cents")
    val paymentStatus = text("payment_status").default("NONE") // NONE | DUE | PAID | WAIVED
    override val primaryKey = PrimaryKey(id)
}
