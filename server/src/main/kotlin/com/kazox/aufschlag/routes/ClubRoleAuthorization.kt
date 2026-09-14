package com.kazox.aufschlag.routes

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.db.withTransaction
import com.kazox.aufschlag.repositories.ClubRepository
import com.kazox.aufschlag.repositories.MembershipRepository
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ktor.ext.inject

/**
 * Club-scoped tenant-isolation guard. Wraps a route subtree so every request under it 404s if
 * the `{clubId}` path segment doesn't name a real club, and 403s unless the caller is an ACTIVE
 * member holding at least [minRole] in it. Must sit inside `authenticate("auth-jwt")`, below a
 * route where `{clubId}` is already a resolved path parameter.
 *
 * Centralizing the check here — instead of each service re-implementing it, as
 * `MembershipService` used to — is what lets milestone-2 routes (courts/bookings) get tenant
 * isolation for free by wrapping in this, rather than every new service needing to remember it
 * itself — every request under a club must check the caller's membership in that club.
 */
fun Route.withClubRole(minRole: MembershipRole, build: Route.() -> Unit): Route {
    val guarded = createChild(ClubRoleRouteSelector(minRole))
    guarded.install(ClubRoleAuthorization) { this.minRole = minRole }
    guarded.build()
    return guarded
}

private class ClubRoleRouteSelector(private val minRole: MembershipRole) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(withClubRole $minRole)"
}

private class ClubRoleAuthorizationConfig {
    var minRole: MembershipRole = MembershipRole.MEMBER
}

private val ROLE_RANK = mapOf(MembershipRole.MEMBER to 0, MembershipRole.ADMIN to 1, MembershipRole.OWNER to 2)

private val ClubRoleAuthorization =
    createRouteScopedPlugin("ClubRoleAuthorization", ::ClubRoleAuthorizationConfig) {
        val clubs by application.inject<ClubRepository>()
        val memberships by application.inject<MembershipRepository>()
        val db by application.inject<Database>()

        // Must run after JWT verification attaches the principal — onCall hooks into the
        // generic Plugins phase, which the Authentication plugin's own check runs after
        // (AuthenticatePhase is inserted *after* Plugins), so onCall here would see no
        // principal yet. AuthenticationChecked is Ktor's dedicated post-auth hook.
        on(AuthenticationChecked) { call ->
            val minRole = pluginConfig.minRole
            val clubId = call.parameters.uuid("clubId")
            val userId = call.authenticatedUserId()
            withTransaction(db) {
                clubs.findById(clubId) ?: throw ApiException.notFound("Club not found")
                val membership = memberships.findActive(userId, clubId)
                    ?: throw ApiException.forbidden("Not a member of this club")
                if (ROLE_RANK.getValue(membership.role) < ROLE_RANK.getValue(minRole)) {
                    throw ApiException.forbidden("Requires $minRole role or higher")
                }
            }
        }
    }
