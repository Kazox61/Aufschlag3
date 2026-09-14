package com.kazox.aufschlag.routes

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.api.club.ApplyToClubRequest
import com.kazox.aufschlag.api.club.CreateClubRequest
import com.kazox.aufschlag.api.club.MembershipDecisionRequest
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.UpdateClubProfileRequest
import com.kazox.aufschlag.api.club.UpdateMemberRequest
import com.kazox.aufschlag.services.ClubService
import com.kazox.aufschlag.services.MembershipService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

fun Route.clubRoutes() {
    val clubService by inject<ClubService>()
    val membershipService by inject<MembershipService>()

    route("/clubs") {
        // Public club directory — no login required.
        get {
            val (limit, cursor) = call.pageParams()
            val query = call.request.queryParameters["search"]
            call.respond(clubService.search(query, limit, cursor))
        }

        authenticate("auth-jwt") {
            // Super-admin only in v1 — clubs onboard manually.
            post {
                val request = call.receive<CreateClubRequest>()
                call.respond(HttpStatusCode.Created, clubService.create(call.authenticatedUserId(), request))
            }

            route("/{clubId}") {
                route("/applications") {
                    // Mitgliedsantrag — any authenticated user.
                    post {
                        val request = call.receive<ApplyToClubRequest>()
                        val response = membershipService.apply(call.authenticatedUserId(), call.clubId(), request)
                        call.respond(HttpStatusCode.Created, response)
                    }
                    withClubRole(MembershipRole.ADMIN) {
                        get {
                            val (limit, cursor) = call.pageParams()
                            call.respond(membershipService.listApplications(call.clubId(), limit, cursor))
                        }
                        // approve sets ACTIVE, reject deletes the PENDING row.
                        put("/{applicationId}") {
                            val request = call.receive<MembershipDecisionRequest>()
                            val clubId = call.clubId()
                            val applicationId = call.applicationId()
                            if (request.approve) {
                                call.respond(membershipService.approve(clubId, applicationId))
                            } else {
                                membershipService.reject(clubId, applicationId)
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                    }
                }
                withClubRole(MembershipRole.ADMIN) {
                    // "Vereinsdaten" admin settings page.
                    get {
                        call.respond(clubService.get(call.clubId()))
                    }
                    put {
                        val request = call.receive<UpdateClubProfileRequest>()
                        call.respond(clubService.updateProfile(call.clubId(), request))
                    }
                    get("/members") {
                        val (limit, cursor) = call.pageParams()
                        call.respond(membershipService.listMembers(call.clubId(), limit, cursor))
                    }
                    // Role/status management — the service branches on the target's role
                    // (an ADMIN target needs an OWNER caller), which withClubRole's single
                    // min-role gate can't express, so the caller id is passed through.
                    put("/members/{membershipId}") {
                        val request = call.receive<UpdateMemberRequest>()
                        call.respond(
                            membershipService.updateMember(
                                call.authenticatedUserId(),
                                call.clubId(),
                                call.parameters.uuid("membershipId"),
                                request,
                            ),
                        )
                    }
                }

                // Self-service — the caller acts on their own membership, no role check needed.
                route("/membership") {
                    post("/pause") {
                        call.respond(membershipService.pause(call.authenticatedUserId(), call.clubId()))
                    }
                    post("/resume") {
                        call.respond(membershipService.resume(call.authenticatedUserId(), call.clubId()))
                    }
                }

                courtRoutes()
                bookingRoutes()
            }
        }
    }
}

internal fun ApplicationCall.clubId(): Uuid = parameters.uuid("clubId")

private fun ApplicationCall.applicationId(): Uuid = parameters.uuid("applicationId")

internal fun Parameters.uuid(name: String): Uuid =
    this[name]?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: throw ApiException.validation("Invalid $name")

internal fun ApplicationCall.pageParams(): Pair<Int, String?> {
    val limit = request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
    val cursor = request.queryParameters["cursor"]
    return limit to cursor
}

private const val DEFAULT_PAGE_SIZE = 20
