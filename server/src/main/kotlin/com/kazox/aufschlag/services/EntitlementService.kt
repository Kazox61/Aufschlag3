package com.kazox.aufschlag.services

import com.kazox.aufschlag.ApiException
import com.kazox.aufschlag.repositories.ClubRow

/**
 * Single point for plan/feature and club-status gating (PLANNING.md "Entitlements") — call
 * sites go through here instead of scattering their own `if (status == ...)` checks. Beta
 * plan always-allows; this stub only encodes club-status semantics so far.
 */
class EntitlementService {

    /** Statuses excluded from the public club directory (today: ARCHIVED only). Returned as a
     *  set rather than a per-row predicate so callers can push the filter down to the DB query
     *  and keep keyset pagination correct. */
    fun directoryHiddenStatuses(): Set<String> = setOf(STATUS_ARCHIVED)

    /** SUSPENDED/ARCHIVED clubs don't accept new Mitgliedsanträge. */
    fun requireAcceptingApplications(club: ClubRow) {
        if (club.status == STATUS_SUSPENDED || club.status == STATUS_ARCHIVED) {
            throw ApiException.forbidden("This club is not accepting new applications")
        }
    }

    /** ARCHIVED clubs are read-only, even for admins. */
    fun requireWritable(club: ClubRow) {
        if (club.status == STATUS_ARCHIVED) {
            throw ApiException.forbidden("This club is archived and read-only")
        }
    }

    companion object {
        private const val STATUS_SUSPENDED = "SUSPENDED"
        private const val STATUS_ARCHIVED = "ARCHIVED"
    }
}
