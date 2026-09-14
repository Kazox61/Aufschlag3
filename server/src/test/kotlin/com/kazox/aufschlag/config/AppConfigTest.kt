package com.kazox.aufschlag.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun `dev defaults apply once JWT_SECRET is provided`() {
        val config = AppConfig.fromEnv(mapOf("JWT_SECRET" to "x".repeat(MIN_JWT_SECRET_LENGTH)))
        assertEquals(8080, config.port)
        assertEquals("jdbc:postgresql://localhost:5432/aufschlag", config.database.url)
    }
}
