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

class RefreshRotationTest {

    private val noGrace = testAuthConfig.copy(graceWindow = Duration.ZERO)

    @Test
    fun `refresh rotates - new token works, reuse outside grace kills the whole family`() =
        authTestApp(auth = noGrace) { client ->
            val initial = client.register(uniqueEmail("rotate")).body<TokenPairResponse>()

            val rotated = client.refresh(initial.refreshToken)
            assertEquals(HttpStatusCode.OK, rotated.status)
            val next = rotated.body<TokenPairResponse>()
            assertEquals(HttpStatusCode.OK, client.me(next.accessToken).status)

            // replaying the rotated (old) token with no grace = theft signal
            assertEquals(HttpStatusCode.Unauthorized, client.refresh(initial.refreshToken).status)
            // the whole family is dead, including the fresh successor
            assertEquals(HttpStatusCode.Unauthorized, client.refresh(next.refreshToken).status)
        }

    @Test
    fun `refresh retry inside grace window returns the same pair and family survives`() =
        authTestApp { client ->
            val initial = client.register(uniqueEmail("grace")).body<TokenPairResponse>()

            val first = client.refresh(initial.refreshToken).body<TokenPairResponse>()
            // the lost-response client retry
            val retryResponse = client.refresh(initial.refreshToken)
            assertEquals(HttpStatusCode.OK, retryResponse.status)
            assertEquals(first, retryResponse.body<TokenPairResponse>())

            // family stays alive: the successor still rotates normally
            assertEquals(HttpStatusCode.OK, client.refresh(first.refreshToken).status)
        }

    @Test
    fun `replay inside grace window whose successor was already used kills the family`() =
        authTestApp { client ->
            val initial = client.register(uniqueEmail("chain")).body<TokenPairResponse>()

            // client legitimately rotates twice: initial → second → third
            val second = client.refresh(initial.refreshToken).body<TokenPairResponse>()
            val third = client.refresh(second.refreshToken).body<TokenPairResponse>()

            // a thief replaying the initial token inside the grace window must NOT be
            // handed the successor: it was already used, so this can't be a lost-response
            // retry — the family dies, including the live head
            assertEquals(HttpStatusCode.Unauthorized, client.refresh(initial.refreshToken).status)
            assertEquals(HttpStatusCode.Unauthorized, client.refresh(third.refreshToken).status)
        }

    @Test
    fun `two concurrent refreshes create exactly one successor`() = authTestApp { client ->
        val initial = client.register(uniqueEmail("concurrent")).body<TokenPairResponse>()

        val responses = coroutineScope {
            listOf(
                async { client.refresh(initial.refreshToken) },
                async { client.refresh(initial.refreshToken) },
            ).awaitAll()
        }

        val successes = responses.filter { it.status == HttpStatusCode.OK }
        assertTrue(successes.isNotEmpty(), "at least one refresh must win")
        // any successful responses must be the SAME pair (atomic rotation, no double mint)
        val pairs = successes.map { it.body<TokenPairResponse>() }.toSet()
        assertEquals(1, pairs.size)

        // DB ground truth: original + exactly one successor in the family
        assertEquals(2, refreshTokenFamilySize(initial.refreshToken))
    }

    @Test
    fun `logout revokes the family`() = authTestApp { client ->
        val tokens = client.register(uniqueEmail("logout")).body<TokenPairResponse>()

        assertEquals(HttpStatusCode.NoContent, client.logout(tokens.refreshToken).status)
        assertEquals(HttpStatusCode.Unauthorized, client.refresh(tokens.refreshToken).status)
    }
}
