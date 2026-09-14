package com.kazox.aufschlag.api.club

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ClubStatus { TRIAL, ACTIVE, SUSPENDED, ARCHIVED }

@Serializable
enum class MembershipRole { OWNER, ADMIN, MEMBER }

@Serializable
enum class MembershipStatus { PENDING, ACTIVE, PAUSED, SUSPENDED, ENDED }

/** Clubs onboard manually in v1 (PLANNING.md); [ownerEmail] must already have an account
 *  and becomes the club's initial OWNER membership. */
@Serializable
data class CreateClubRequest(
    val name: String,
    val slug: String,
    val ownerEmail: String,
    val timezone: String = "Europe/Berlin",
)

@Serializable
data class ClubResponse(
    val id: String,
    val name: String,
    val slug: String,
    val status: ClubStatus,
    val timezone: String,
    val address: String? = null,
    val contactEmail: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val logoUrl: String? = null,
)

/** PUT /clubs/{clubId} (admin) — the "Vereinsdaten" profile shown in the admin app's settings
 *  page. Distinct from [ClubSettings] (opening hours / booking rules, stored as one jsonb blob):
 *  these are plain display/contact fields, so they get their own columns. Null fields clear the
 *  value (unlike [UpdateMemberRequest]'s null-means-unchanged convention) since the settings form
 *  always submits the full record it loaded. */
@Serializable
data class UpdateClubProfileRequest(
    val name: String,
    val address: String? = null,
    val contactEmail: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val logoUrl: String? = null,
)

/** Mitgliedsantrag: application answers, free-form for now (per-club configurable forms come later). */
@Serializable
data class ApplyToClubRequest(
    val applicationData: JsonObject? = null,
)

@Serializable
data class MembershipDecisionRequest(
    val approve: Boolean,
)

/** [userName]/[userEmail] are joined in on the admin-facing reads (applications/members lists and
 *  the decision/update responses) so admin UIs never render bare user ids; they stay null on the
 *  applicant's own responses. Exposing the email to club admins is deliberate — they process the
 *  member's application, and the per-club AVV covers that processing (PLANNING.md GDPR section). */
@Serializable
data class MembershipResponse(
    val id: String,
    val userId: String,
    val clubId: String,
    val role: MembershipRole,
    val status: MembershipStatus,
    val applicationData: JsonObject? = null,
    val userName: String? = null,
    val userEmail: String? = null,
)

/** PUT /clubs/{clubId}/members/{membershipId} (admin) — null field = unchanged. Rules (enforced
 *  server-side, see docs/admin-webapp.md §1.2): status may move between ACTIVE/PAUSED/SUSPENDED
 *  or to ENDED ("remove member" — the row stays as history and the ex-member can re-apply); role
 *  only between MEMBER and ADMIN (OWNER is reassigned by the super-admin, never through this);
 *  a target ADMIN can only be modified by the OWNER; PENDING rows go through the application
 *  decision endpoint instead; callers cannot target their own membership. */
@Serializable
data class UpdateMemberRequest(
    val role: MembershipRole? = null,
    val status: MembershipStatus? = null,
)

/** GET /me/memberships — joined with the club's own info (name, timezone, ...) so the club
 *  switcher can render a row without a separate lookup per membership. */
@Serializable
data class MyMembershipResponse(
    val membershipId: String,
    val role: MembershipRole,
    val status: MembershipStatus,
    val club: ClubResponse,
)
