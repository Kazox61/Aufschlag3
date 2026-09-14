package com.kazox.aufschlag

import com.kazox.aufschlag.api.auth.TokenPairResponse
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class PasswordResetTest {

    @Test
    fun `request for unknown email returns 204 and sends nothing`() {
        val mailer = RecordingMailer()
        authTestApp(mailer = mailer) { client ->
            val response = client.requestPasswordReset(uniqueEmail("nobody"))
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertTrue(mailer.sent.isEmpty())
        }
    }

    @Test
    fun `full reset - new password works, old password and all sessions dead, token single-use`() {
        val mailer = RecordingMailer()
        authTestApp(mailer = mailer) { client ->
            val email = uniqueEmail("reset")
            val tokens = client.register(email, password = "old-password-1").body<TokenPairResponse>()

            assertEquals(HttpStatusCode.NoContent, client.requestPasswordReset(email).status)
            val resetToken = mailer.awaitTokenFor(email)

            assertEquals(HttpStatusCode.NoContent, client.confirmPasswordReset(resetToken, "new-password-1").status)

            // old password dead, new one works
            assertEquals(HttpStatusCode.Unauthorized, client.login(email, "old-password-1").status)
            assertEquals(HttpStatusCode.OK, client.login(email, "new-password-1").status)

            // every refresh-token family is revoked
            assertEquals(HttpStatusCode.Unauthorized, client.refresh(tokens.refreshToken).status)

            // the reset token is single-use
            assertEquals(HttpStatusCode.Unauthorized, client.confirmPasswordReset(resetToken, "another-pass-1").status)
        }
    }

    @Test
    fun `reset token survives exactly one of two concurrent confirms`() {
        val mailer = RecordingMailer()
        authTestApp(mailer = mailer) { client ->
            val email = uniqueEmail("race")
            client.register(email, password = "old-password-1")
            client.requestPasswordReset(email)
            val resetToken = mailer.awaitTokenFor(email)

            val responses = coroutineScope {
                listOf(
                    async { client.confirmPasswordReset(resetToken, "first-password-1") },
                    async { client.confirmPasswordReset(resetToken, "second-password-1") },
                ).awaitAll()
            }

            // atomic claim: the token is consumed exactly once, the loser gets 401
            assertEquals(1, responses.count { it.status == HttpStatusCode.NoContent })
            assertEquals(1, responses.count { it.status == HttpStatusCode.Unauthorized })
        }
    }

    @Test
    fun `expired reset token is rejected`() {
        val mailer = RecordingMailer()
        authTestApp(auth = testAuthConfig.copy(resetTokenTtl = Duration.ZERO), mailer = mailer) { client ->
            val email = uniqueEmail("expired")
            client.register(email)
            client.requestPasswordReset(email)
            val resetToken = mailer.awaitTokenFor(email)

            assertEquals(HttpStatusCode.Unauthorized, client.confirmPasswordReset(resetToken, "new-password-1").status)
        }
    }

    @Test
    fun `reset request is normalized - mixed-case email still reaches the account`() {
        val mailer = RecordingMailer()
        authTestApp(mailer = mailer) { client ->
            val email = uniqueEmail("normalize")
            client.register(email)

            client.requestPasswordReset(email.uppercase())
            mailer.awaitTokenFor(email) // mail goes to the normalized address...
            assertTrue(mailer.sent.all { it.to == email }, "...and only to it")
        }
    }
}
