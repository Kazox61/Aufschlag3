package com.kazox.aufschlag.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationRulesTest {

    private fun valid(email: String) = email.matches(RegistrationRules.EMAIL_REGEX)

    @Test
    fun `accepts ordinary addresses`() {
        listOf("anna@example.com", "a.b+tag@sub.example.co.uk", "x@y.zz").forEach {
            assertTrue(valid(it), it)
        }
    }

    @Test
    fun `rejects whitespace, multiple at-signs and missing parts`() {
        listOf("anna @example.com", "anna@exam ple.com", "a@b@c.d", "@example.com", "anna@", "anna@example", "anna").forEach {
            assertFalse(valid(it), it)
        }
    }
}
