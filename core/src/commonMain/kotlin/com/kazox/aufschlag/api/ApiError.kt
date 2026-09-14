package com.kazox.aufschlag.api

import kotlinx.serialization.Serializable

/**
 * The single error shape every non-2xx API response carries.
 * Clients branch on [code], never on [message] (which is for humans/logs).
 * [field] is set only for [ErrorCode.VALIDATION_FAILED] on endpoints with more than one
 * user-editable field (e.g. register) — the name of the field that failed, so the client can
 * show the error inline instead of falling back to a generic message.
 */
@Serializable
data class ApiError(
    val code: ErrorCode,
    val message: String,
    val field: String? = null,
)

@Serializable
enum class ErrorCode {
    VALIDATION_FAILED,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    INTERNAL_ERROR,

    // auth
    EMAIL_TAKEN,
    INVALID_CREDENTIALS,
    INVALID_TOKEN,

    // clubs & memberships
    SLUG_TAKEN,
    ALREADY_MEMBER,

    // bookings
    BOOKING_CONFLICT,
    ADVANCE_WINDOW_EXCEEDED,
    BOOKING_LIMIT_EXCEEDED,
    CANCELLATION_WINDOW_PASSED,
}
