package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `health returns ok when database is up`() = authTestApp { client ->
        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"ok\""))
    }

    @Test
    fun `unknown route returns the shared error shape`() = authTestApp { client ->
        val response = client.get("/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("NOT_FOUND"))
    }

    @Test
    fun `a body missing a required field is a 400 that names the field`() = authTestApp { client ->
        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"someone@example.test"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ApiError>()
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
        assertTrue("password" in error.message, error.message)
    }

    @Test
    fun `a request body over the limit is rejected with 413`() = authTestApp { client ->
        val padding = "x".repeat(MAX_REQUEST_BODY_BYTES.toInt() + 1)
        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$padding","password":"p"}""")
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }
}
