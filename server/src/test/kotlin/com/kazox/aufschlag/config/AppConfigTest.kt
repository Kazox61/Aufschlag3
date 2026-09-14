package com.kazox.aufschlag.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppConfigTest {

    @Test
    fun `startup fails without JWT_SECRET - no silent default secret`() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(emptyMap()) }
    }

    @Test
    fun `startup fails on a too-short JWT_SECRET`() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnv(mapOf("JWT_SECRET" to "short"))
        }
    }

    @Test
    fun `startup fails without MAIL_API_KEY unless log-only mail is opted into`() {
        val secret = "x".repeat(MIN_JWT_SECRET_LENGTH)
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("JWT_SECRET" to secret)) }
        assertFailsWith<IllegalStateException> {
            AppConfig.fromEnv(mapOf("JWT_SECRET" to secret, "MAIL_LOG_ONLY" to "false"))
        }
        // a blank key is "not set", not a key
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("JWT_SECRET" to secret, "MAIL_API_KEY" to "  ")) }
        assertNull(AppConfig.fromEnv(mapOf("JWT_SECRET" to secret, "MAIL_LOG_ONLY" to "true")).mail.apiKey)
        assertEquals("re_key", AppConfig.fromEnv(mapOf("JWT_SECRET" to secret, "MAIL_API_KEY" to "re_key")).mail.apiKey)
    }

    @Test
    fun `dev defaults apply once the required secrets are provided`() {
        val config = AppConfig.fromEnv(mapOf("JWT_SECRET" to "x".repeat(MIN_JWT_SECRET_LENGTH), "MAIL_LOG_ONLY" to "true"))
        assertEquals(8080, config.port)
        assertEquals("jdbc:postgresql://localhost:5432/aufschlag", config.database.url)
    }
}
