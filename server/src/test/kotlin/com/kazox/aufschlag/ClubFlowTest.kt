package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.ClubStatus
import com.kazox.aufschlag.api.club.UpdateClubProfileRequest
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClubFlowTest {

    @Test
    fun `non-super-admin cannot create a club`() = authTestApp { client ->
        val caller = client.register(uniqueEmail("caller")).body<TokenPairResponse>()
        val ownerEmail = uniqueEmail("owner")
        client.register(ownerEmail)

        val response = client.createClub(caller.accessToken, "Tennisclub Nord", uniqueSlug(), ownerEmail)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ErrorCode.FORBIDDEN, response.body<ApiError>().code)
    }

    @Test
    fun `super-admin creates a club and it becomes searchable`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        client.register(ownerEmail)
        val slug = uniqueSlug()

        val created = client.createClub(admin.accessToken, "Tennisclub Nord", slug, ownerEmail)
        assertEquals(HttpStatusCode.Created, created.status)
        val club = created.body<ClubResponse>()
        assertEquals(slug, club.slug)
        assertEquals(ClubStatus.TRIAL, club.status)

        val found = client.searchClubs(query = slug).body<Page<ClubResponse>>()
        assertTrue(found.items.any { it.id == club.id })
    }

    @Test
    fun `create club with unknown owner email is a validation error`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)

        val response = client.createClub(admin.accessToken, "Tennisclub Nord", uniqueSlug(), uniqueEmail("ghost"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun `duplicate slug returns 409 SLUG_TAKEN`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val owner1 = uniqueEmail("owner1")
        val owner2 = uniqueEmail("owner2")
        client.register(owner1)
        client.register(owner2)
        val slug = uniqueSlug()

        assertEquals(HttpStatusCode.Created, client.createClub(admin.accessToken, "Club One", slug, owner1).status)
        val duplicate = client.createClub(admin.accessToken, "Club Two", slug, owner2)
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(ErrorCode.SLUG_TAKEN, duplicate.body<ApiError>().code)
    }

    @Test
    fun `archived clubs are hidden from the public directory`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        client.register(ownerEmail)
        val slug = uniqueSlug()

        val club = client.createClub(admin.accessToken, "Archived Club", slug, ownerEmail).body<ClubResponse>()
        setClubStatus(club.id, "ARCHIVED")

        val found = client.searchClubs(query = slug).body<Page<ClubResponse>>()
        assertTrue(found.items.none { it.id == club.id })
    }

    @Test
    fun `club search paginates with limit and cursor`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val sharedPrefix = "pg-${kotlin.uuid.Uuid.random()}"
        val ids = (1..3).map { i ->
            val ownerEmail = uniqueEmail("owner$i")
            client.register(ownerEmail)
            client.createClub(admin.accessToken, "Paging Club $i", "$sharedPrefix-$i", ownerEmail)
                .body<ClubResponse>().id
        }

        val firstPage = client.searchClubs(query = sharedPrefix, limit = 2).body<Page<ClubResponse>>()
        assertEquals(2, firstPage.items.size)
        assertTrue(firstPage.nextCursor != null)

        val secondPage = client.searchClubs(query = sharedPrefix, limit = 2, cursor = firstPage.nextCursor)
            .body<Page<ClubResponse>>()
        assertEquals(1, secondPage.items.size)

        val allIds = (firstPage.items + secondPage.items).map { it.id }.toSet()
        assertEquals(ids.toSet(), allIds)
    }

    @Test
    fun `owner updates the club profile and it round-trips`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = client.register(ownerEmail).body<TokenPairResponse>()
        val club = client.createClub(admin.accessToken, "TC Lautlingen", uniqueSlug(), ownerEmail)
            .body<ClubResponse>()

        val update = client.updateClubProfile(
            owner.accessToken,
            club.id,
            UpdateClubProfileRequest(
                name = "TC Lautlingen 1972 e.V.",
                address = "Im Espach 14, 72459 Albstadt-Lautlingen",
                contactEmail = "vorstand@tc-lautlingen.de",
                phone = "+49 7431 123456",
                website = "https://tc-lautlingen.de",
                logoUrl = "https://tc-lautlingen.de/logo.png",
            ),
        )
        assertEquals(HttpStatusCode.OK, update.status)
        val updated = update.body<ClubResponse>()
        assertEquals("TC Lautlingen 1972 e.V.", updated.name)
        assertEquals("Im Espach 14, 72459 Albstadt-Lautlingen", updated.address)
        assertEquals("vorstand@tc-lautlingen.de", updated.contactEmail)

        val fetched = client.getClub(owner.accessToken, club.id).body<ClubResponse>()
        assertEquals(updated, fetched)
    }

    @Test
    fun `a plain member cannot update the club profile`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        client.register(ownerEmail)
        val club = client.createClub(admin.accessToken, "Club", uniqueSlug(), ownerEmail).body<ClubResponse>()

        val outsider = client.register(uniqueEmail("outsider")).body<TokenPairResponse>()
        val response = client.updateClubProfile(outsider.accessToken, club.id, UpdateClubProfileRequest(name = "Hijacked"))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `blank name is a validation error`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = client.register(ownerEmail).body<TokenPairResponse>()
        val club = client.createClub(admin.accessToken, "Club", uniqueSlug(), ownerEmail).body<ClubResponse>()

        val response = client.updateClubProfile(owner.accessToken, club.id, UpdateClubProfileRequest(name = "   "))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun `blank optional fields are stored as null`() = authTestApp { client ->
        val adminEmail = uniqueEmail("admin")
        val admin = client.register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = client.register(ownerEmail).body<TokenPairResponse>()
        val club = client.createClub(admin.accessToken, "Club", uniqueSlug(), ownerEmail).body<ClubResponse>()

        val update = client.updateClubProfile(
            owner.accessToken,
            club.id,
            UpdateClubProfileRequest(name = "Club", address = "  ", contactEmail = ""),
        ).body<ClubResponse>()
        assertNull(update.address)
        assertNull(update.contactEmail)
    }
}
