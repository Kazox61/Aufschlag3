package com.kazox.aufschlag.auth

/**
 * Registration field constraints — the single source of truth for both the server
 * (`AuthService.validateEmail`/`validateName`/`validatePassword`) and the client's pre-submit
 * `RegisterValidator`, so the two can't silently drift apart the way two hand-copied constant
 * sets would.
 */
object RegistrationRules {
    val EMAIL_REGEX: Regex = Regex(".+@.+\\..+")
    const val EMAIL_MAX_LENGTH: Int = 255
    const val NAME_MAX_LENGTH: Int = 100
    const val PASSWORD_MIN_LENGTH: Int = 8
    const val PASSWORD_MAX_LENGTH: Int = 128
}
