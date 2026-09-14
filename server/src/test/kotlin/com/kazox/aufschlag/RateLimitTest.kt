package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class RateLimitTest {

    @Test
    fun `auth routes trip the per-IP rate limit`() =
        authTestApp(auth = testAuthConfig.copy(rateLimit = 3)) { client ->
            val email = uniqueEmail("ratelimit")
            repeat(3) {
                assertEquals(HttpStatusCode.Unauthorized, client.login(email, "wrong-password-1").status)
            }
            val limited = client.login(email, "wrong-password-1")
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals(ErrorCode.RATE_LIMITED, limited.body<ApiError>().code)
        }

    @Test
    fun `routes outside the auth group are not rate limited`() =
        authTestApp(auth = testAuthConfig.copy(rateLimit = 2)) { client ->
            repeat(5) {
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
            }
        }
}
