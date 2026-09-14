package com.kazox.aufschlag

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CORS exists solely for the admin web app (docs/admin-webapp.md) — a browser preflight from an
 *  allowed origin must succeed, everything else must not get an allow header. Uses the default
 *  origins baked into [com.kazox.aufschlag.module] (the adminApp dev server on :8081). */
class CorsTest {

    @Test
    fun `preflight from an allowed origin is approved`() = authTestApp { client ->
        val response = client.options("/v1/me") {
            header(HttpHeaders.Origin, "http://localhost:8081")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "Authorization")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("http://localhost:8081", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `preflight from a disallowed origin gets no allow header`() = authTestApp { client ->
        val response = client.options("/v1/me") {
            header(HttpHeaders.Origin, "http://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `requests without an Origin header are unaffected`() = authTestApp { client ->
        // Mobile clients never send Origin — CORS must not interfere with them.
        val response = client.register(uniqueEmail("cors"))
        assertEquals(HttpStatusCode.Created, response.status)
    }
}
