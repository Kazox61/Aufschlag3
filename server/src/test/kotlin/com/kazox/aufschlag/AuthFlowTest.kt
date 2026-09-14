package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.auth.UserResponse
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthFlowTest {

    @Test
    fun `register issues tokens and me returns the user`() = authTestApp { client ->
        val email = uniqueEmail()
        val response = client.register(email, name = "Anna Athlete")
        assertEquals(HttpStatusCode.Created, response.status)

        val tokens = response.body<TokenPairResponse>()
        val me = client.me(tokens.accessToken)
        assertEquals(HttpStatusCode.OK, me.status)

        val user = me.body<UserResponse>()
        assertEquals(email.lowercase(), user.email)
        assertEquals("Anna Athlete", user.name)
    }

    @Test
    fun `register with existing email in different casing returns 409 EMAIL_TAKEN`() = authTestApp { client ->
        val email = uniqueEmail("casing")
        assertEquals(HttpStatusCode.Created, client.register(email).status)

        val duplicate = client.register(email.uppercase())
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(ErrorCode.EMAIL_TAKEN, duplicate.body<ApiError>().code)
    }

    @Test
    fun `login is case-insensitive on email`() = authTestApp { client ->
        val email = uniqueEmail("login")
        client.register(email, password = "correct-horse-1")

        val response = client.login(email.uppercase(), "correct-horse-1")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `login with wrong password returns 401 INVALID_CREDENTIALS`() = authTestApp { client ->
        val email = uniqueEmail("wrongpw")
        client.register(email, password = "correct-horse-1")

        val response = client.login(email, "wrong-password-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorCode.INVALID_CREDENTIALS, response.body<ApiError>().code)
    }

    @Test
    fun `login with unknown email returns the same 401 as wrong password`() = authTestApp { client ->
        val response = client.login(uniqueEmail("ghost"), "whatever-123")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorCode.INVALID_CREDENTIALS, response.body<ApiError>().code)
    }

    @Test
    fun `me without or with garbage token returns 401`() = authTestApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.me(null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.me("not-a-jwt").status)
    }

    @Test
    fun `register validates email and password`() = authTestApp { client ->
        val badEmail = client.register("not-an-email", password = "long-enough-1")
        assertEquals(HttpStatusCode.BadRequest, badEmail.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, badEmail.body<ApiError>().code)

        val shortPassword = client.register(uniqueEmail(), password = "short")
        assertEquals(HttpStatusCode.BadRequest, shortPassword.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, shortPassword.body<ApiError>().code)
    }
}
