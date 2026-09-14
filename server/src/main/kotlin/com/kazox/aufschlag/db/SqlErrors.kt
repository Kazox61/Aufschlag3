package com.kazox.aufschlag.db

import java.sql.SQLException

/** Postgres SQLSTATE codes the services translate into domain conflicts (23xxx = integrity
 *  constraint violation). Constraints are the source of truth for these invariants; the
 *  application pre-checks only to give a nicer error on the common path. */
private const val UNIQUE_VIOLATION = "23505"
private const val EXCLUSION_VIOLATION = "23P01"

/** True if [this] or any cause is a unique-index violation — e.g. a concurrent register with
 *  the same email, or a concurrent apply that loses the partial-unique-index race. */
fun Throwable.isUniqueViolation(): Boolean = hasSqlState(UNIQUE_VIOLATION)

/** True if [this] or any cause is an EXCLUDE-constraint violation — the bookings
 *  no-double-booking rule (`tstzrange && tstzrange` on the same court). */
fun Throwable.isExclusionViolation(): Boolean = hasSqlState(EXCLUSION_VIOLATION)

private fun Throwable.hasSqlState(state: String): Boolean =
    generateSequence(this) { it.cause }.any { it is SQLException && it.sqlState == state }
