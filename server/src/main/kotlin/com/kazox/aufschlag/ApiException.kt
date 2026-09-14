package com.kazox.aufschlag

import com.kazox.aufschlag.api.ErrorCode
import io.ktor.http.HttpStatusCode

/** Thrown anywhere below the routing layer; StatusPages turns it into the shared [com.kazox.aufschlag.api.ApiError] shape. */
class ApiException(
    val status: HttpStatusCode,
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : RuntimeException(message) {
    companion object {
        fun notFound(message: String = "Resource not found") =
            ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, message)

        fun unauthorized(message: String = "Authentication required") =
            ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, message)

        fun forbidden(message: String = "Access denied") =
            ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, message)

        /** [field] names the request field that failed, so multi-field endpoints (e.g. register)
         *  can point the client at the right one instead of a generic message. */
        fun validation(message: String, field: String? = null) =
            ApiException(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, message, field)

        fun conflict(code: ErrorCode, message: String) =
            ApiException(HttpStatusCode.Conflict, code, message)
    }
}
