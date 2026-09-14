package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.AppJson
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.ClubSettings
import com.kazox.aufschlag.api.club.CreateClubRequest
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import com.kazox.aufschlag.api.club.UpdateClubProfileRequest
import com.kazox.aufschlag.db.isUniqueViolation
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.ClubRow
import com.kazox.aufschlag.repositories.MembershipRepository
import com.kazox.aufschlag.repositories.UserRepository
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.uuid.Uuid

class ClubService(
    private val db: Database,
    private val clubs: ClubRepository,
    private val memberships: MembershipRepository,
    private val users: UserRepository,
    private val entitlements: EntitlementService,
) {
    /** Super-admin only in v1 — clubs onboard manually. Creates the club and
     *  its initial OWNER membership in one transaction. */
    suspend fun create(callerUserId: Uuid, request: CreateClubRequest): ClubResponse {
        val name = request.name.trim()
        val slug = request.slug.trim().lowercase()
        val timezone = request.timezone.trim()
        val ownerEmail = request.ownerEmail.trim().lowercase()
        validateName(name)
        validateSlug(slug)
        validateTimezone(timezone)
        val settingsJson = defaultSettingsJson()

        return try {
            withTransaction(db) {
                val caller = users.findById(callerUserId) ?: throw ApiException.unauthorized()
                if (!caller.isSuperAdmin) throw ApiException.forbidden("Only a super-admin can create clubs")

                val owner = users.findByEmail(ownerEmail)
                    ?: throw ApiException.validation("ownerEmail must belong to an existing account")

                if (clubs.findBySlug(slug) != null) throw slugTaken()
                val clubId = clubs.create(name, slug, timezone, settingsJson)
                memberships.create(owner.id, clubId, role = MembershipRole.OWNER, status = MembershipStatus.ACTIVE, applicationDataJson = null)
                clubs.findById(clubId)!!.toResponse()
            }
        } catch (e: Exception) {
            // concurrent create with the same slug loses the unique-index race
            if (e.isUniqueViolation()) throw slugTaken() else throw e
        }
    }

    /** Public club directory — no authentication required. ARCHIVED clubs are excluded. */
    suspend fun search(query: String?, limit: Int, cursor: String?): Page<ClubResponse> {
        val keyset = parseKeysetCursor(cursor)
        val pageSize = pageSizeOf(limit)
        val rows = withTransaction(db) {
            clubs.search(query, entitlements.directoryHiddenStatuses(), pageSize + 1, keyset)
        }
        return rows.toPage(pageSize, ClubRow::keyset, ClubRow::toResponse)
    }

    /** Single-club lookup backing the admin settings page — the route gates it to ADMIN+ via
     *  [com.kazox.aufschlag.routes.withClubRole], so no membership check here. */
    suspend fun get(clubId: Uuid): ClubResponse = withTransaction(db) {
        (clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")).toResponse()
    }

    /** "Vereinsdaten" admin settings form (PUT /clubs/{clubId}, ADMIN role). Unlike [create],
     *  fields are free text with no client system depending on their shape beyond length/email
     *  sanity — no slug/timezone here, those aren't editable through this endpoint. */
    suspend fun updateProfile(clubId: Uuid, request: UpdateClubProfileRequest): ClubResponse {
        val name = request.name.trim()
        val address = request.address?.trim()?.ifBlank { null }
        val contactEmail = request.contactEmail?.trim()?.ifBlank { null }
        val phone = request.phone?.trim()?.ifBlank { null }
        val website = request.website?.trim()?.ifBlank { null }
        val logoUrl = request.logoUrl?.trim()?.ifBlank { null }
        validateName(name)
        contactEmail?.let { validateContactEmail(it) }

        return withTransaction(db) {
            clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
            clubs.updateProfile(clubId, name, address, contactEmail, phone, website, logoUrl)
            clubs.findById(clubId)!!.toResponse()
        }
    }

    private fun validateContactEmail(email: String) {
        if (email.length > 254 || !email.contains("@")) throw ApiException.validation("Invalid contact email")
    }

    private fun defaultSettingsJson(): String {
        val settings = ClubSettings()
        settings.validate()
        return AppJson.encodeToString(ClubSettings.serializer(), settings)
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name.length > 100) throw ApiException.validation("Name must be 1-100 characters")
    }

    private fun validateSlug(slug: String) {
        if (!slug.matches(SLUG_REGEX)) {
            throw ApiException.validation("Slug must be 1-50 lowercase letters, digits or hyphens")
        }
    }

    /** Slot/opening-hours resolution needs a real IANA zone (kotlinx.datetime.TimeZone.of
     *  throws on anything else) — caught at creation time, not the first time a slot is rendered. */
    private fun validateTimezone(timezone: String) {
        runCatching { TimeZone.of(timezone) }.getOrElse { throw ApiException.validation("Invalid timezone: $timezone") }
    }

    private fun slugTaken() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.SLUG_TAKEN, "This slug is already taken")

    companion object {
        private val SLUG_REGEX = Regex("[a-z0-9]([a-z0-9-]{0,48}[a-z0-9])?")
    }
}

internal fun ClubRow.toResponse() = ClubResponse(
    id = id.toString(),
    name = name,
    slug = slug,
    status = status,
    timezone = timezone,
    address = address,
    contactEmail = contactEmail,
    phone = phone,
    website = website,
    logoUrl = logoUrl,
)
