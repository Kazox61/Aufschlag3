package com.kazox.aufschlag.api.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class PasswordResetRequest(
    val email: String,
)

@Serializable
data class PasswordResetConfirmRequest(
    val token: String,
    val newPassword: String,
)

@Serializable
data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
)

/** DELETE /me must re-verify the current password — a stolen 15-minute bearer token alone
 *  must not be enough to delete an account. */
@Serializable
data class DeleteAccountRequest(
    val password: String,
)
