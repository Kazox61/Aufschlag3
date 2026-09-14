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

    @Test
    fun `with TRUST_PROXY_HEADERS the limit is keyed on X-Forwarded-For, not the proxy address`() =
        authTestApp(auth = testAuthConfig.copy(rateLimit = 2), trustProxyHeaders = true) { client ->
            val email = uniqueEmail("proxied")
            repeat(2) {
                assertEquals(HttpStatusCode.Unauthorized, client.login(email, "wrong", forwardedFor = "10.0.0.1").status)
            }
            assertEquals(HttpStatusCode.TooManyRequests, client.login(email, "wrong", forwardedFor = "10.0.0.1").status)
            // a different client behind the same proxy has its own bucket
            assertEquals(HttpStatusCode.Unauthorized, client.login(email, "wrong", forwardedFor = "10.0.0.2").status)
        }

    @Test
    fun `without TRUST_PROXY_HEADERS a forged X-Forwarded-For does not escape the limit`() =
        authTestApp(auth = testAuthConfig.copy(rateLimit = 2)) { client ->
            val email = uniqueEmail("forged")
            repeat(2) {
                assertEquals(HttpStatusCode.Unauthorized, client.login(email, "wrong", forwardedFor = "10.0.0.$it").status)
            }
            assertEquals(HttpStatusCode.TooManyRequests, client.login(email, "wrong", forwardedFor = "10.0.0.9").status)
        }
}
