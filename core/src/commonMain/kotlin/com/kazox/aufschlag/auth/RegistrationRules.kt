package com.kazox.aufschlag.auth

/**
 * Registration field constraints — the single source of truth for both the server
 * (`AuthService.validateEmail`/`validateName`/`validatePassword`) and the client's pre-submit
 * `RegisterValidator`, so the two can't silently drift apart the way two hand-copied constant
 * sets would.
 */
object RegistrationRules {
    /** Local part, exactly one `@`, a domain with at least one dot — none of it whitespace.
     *  Deliberately loose beyond that: real-world deliverability is what verification mail is for. */
    val EMAIL_REGEX: Regex = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    const val EMAIL_MAX_LENGTH: Int = 255
    const val NAME_MAX_LENGTH: Int = 100
    const val PASSWORD_MIN_LENGTH: Int = 8
    const val PASSWORD_MAX_LENGTH: Int = 128
}
