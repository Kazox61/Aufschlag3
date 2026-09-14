package com.kazox.aufschlag

import com.kazox.aufschlag.api.ApiError
import com.kazox.aufschlag.api.ErrorCode
import com.kazox.aufschlag.api.Page
import com.kazox.aufschlag.api.auth.TokenPairResponse
import com.kazox.aufschlag.api.auth.UserResponse
import com.kazox.aufschlag.api.booking.BookingResponse
import com.kazox.aufschlag.api.booking.BookingStatus
import com.kazox.aufschlag.api.booking.DayAvailabilityResponse
import com.kazox.aufschlag.api.booking.MyBookingResponse
import com.kazox.aufschlag.api.booking.PaymentStatus
import com.kazox.aufschlag.api.club.ClubResponse
import com.kazox.aufschlag.api.club.MembershipResponse
import com.kazox.aufschlag.api.court.CourtResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class BookingFlowTest {

    private val timeZone = TimeZone.of("Europe/Berlin")

    private fun slotStart(daysFromToday: Int, hour: Int) = dynamicSlotStart(timeZone, daysFromToday, hour)

    private suspend fun HttpClient.newClubWithCourt(
        defaultPriceCents: Int = 1200,
        memberDiscountPct: Int = 100,
    ): Triple<ClubResponse, String, CourtResponse> {
        val adminEmail = uniqueEmail("admin")
        val admin = register(adminEmail).body<TokenPairResponse>()
        makeSuperAdmin(adminEmail)
        val ownerEmail = uniqueEmail("owner")
        val owner = register(ownerEmail).body<TokenPairResponse>()
        val club = createClub(admin.accessToken, "Booking Club", uniqueSlug(), ownerEmail, timezone = "Europe/Berlin")
            .body<ClubResponse>()
        val court = createCourt(
            owner.accessToken,
            club.id,
            defaultPriceCents = defaultPriceCents,
            memberDiscountPct = memberDiscountPct,
        ).body<CourtResponse>()
        return Triple(club, owner.accessToken, court)
    }

    @Test
    fun `a member can book a free slot, price is snapshotted at zero`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt() // 100% member discount

        val response = client.createBooking(ownerToken, club.id, court.id, slotStart(daysFromToday = 3, hour = 10))
        assertEquals(HttpStatusCode.Created, response.status)
        val booking = response.body<BookingResponse>()
        assertEquals(BookingStatus.ACTIVE, booking.status)
        assertEquals(0, booking.finalPriceCents)
        assertEquals(PaymentStatus.NONE, booking.paymentStatus)
    }

    @Test
    fun `a guest can book at full price, fee is DUE`() = authTestApp { client ->
        val (club, _, court) = client.newClubWithCourt(defaultPriceCents = 1200, memberDiscountPct = 100)
        val guest = client.register(uniqueEmail("guest")).body<TokenPairResponse>()

        val response = client.createBooking(guest.accessToken, club.id, court.id, slotStart(daysFromToday = 1, hour = 10))
        assertEquals(HttpStatusCode.Created, response.status)
        val booking = response.body<BookingResponse>()
        assertEquals(1200, booking.finalPriceCents)
        assertEquals(0, booking.discountPct)
        assertEquals(PaymentStatus.DUE, booking.paymentStatus)
    }

    @Test
    fun `double-booking the same slot is rejected as a conflict`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val other = client.register(uniqueEmail("other")).body<TokenPairResponse>()
        val startsAt = slotStart(daysFromToday = 1, hour = 11)

        assertEquals(HttpStatusCode.Created, client.createBooking(ownerToken, club.id, court.id, startsAt).status)
        val conflict = client.createBooking(other.accessToken, club.id, court.id, startsAt)
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals(ErrorCode.BOOKING_CONFLICT, conflict.body<ApiError>().code)
    }

    @Test
    fun `booking too far in advance is rejected`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt() // member default advance window: 7 days

        val response = client.createBooking(ownerToken, club.id, court.id, slotStart(daysFromToday = 30, hour = 10))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.ADVANCE_WINDOW_EXCEEDED, response.body<ApiError>().code)
    }

    @Test
    fun `open booking limit is enforced once reached`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        setClubSettings(club.id, """{"maxOpenBookings":{"MEMBER":1,"PAUSED":5,"GUEST":2}}""")

        val first = client.createBooking(ownerToken, club.id, court.id, slotStart(daysFromToday = 1, hour = 10))
        assertEquals(HttpStatusCode.Created, first.status)

        val second = client.createBooking(ownerToken, club.id, court.id, slotStart(daysFromToday = 1, hour = 11))
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals(ErrorCode.BOOKING_LIMIT_EXCEEDED, second.body<ApiError>().code)
    }

    @Test
    fun `a slot start that doesn't align with the court's grid is rejected`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()

        val response = client.createBooking(ownerToken, club.id, court.id, slotStart(1, 10).plus(15, DateTimeUnit.MINUTE, timeZone))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun `a suspended member cannot book`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE memberships SET status = 'SUSPENDED' WHERE id = ?::uuid").use {
                it.setString(1, application.id)
                it.executeUpdate()
            }
            connection.commit()
        }

        val response = client.createBooking(member.accessToken, club.id, court.id, slotStart(1, 10))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `owner cancels their own future booking, the slot reopens`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val startsAt = slotStart(daysFromToday = 3, hour = 10)
        val booking = client.createBooking(ownerToken, club.id, court.id, startsAt).body<BookingResponse>()

        val cancelled = client.cancelBooking(ownerToken, club.id, booking.id)
        assertEquals(HttpStatusCode.NoContent, cancelled.status)

        val date = startsAt.toLocalDateTime(timeZone).date.toString()
        val day = client.getSlots(ownerToken, club.id, court.id, date).body<DayAvailabilityResponse>()
        val slot = day.slots.first { it.startsAt == startsAt }
        assertTrue(slot.available)
    }

    @Test
    fun `cancelling outside the cancellation window is rejected for a non-admin member`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt() // default cancellationWindowHours = 24
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)
        val memberId = client.me(member.accessToken).body<UserResponse>().id
        val soon = Clock.System.now() + 1.hours
        val bookingId = insertBooking(club.id, court.id, soon.toString(), (soon + 1.hours).toString(), userId = memberId)

        val response = client.cancelBooking(member.accessToken, club.id, bookingId)
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorCode.CANCELLATION_WINDOW_PASSED, response.body<ApiError>().code)
    }

    @Test
    fun `cancelling someone else's booking 404s`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val other = client.register(uniqueEmail("other")).body<TokenPairResponse>()
        val booking = client.createBooking(ownerToken, club.id, court.id, slotStart(1, 10)).body<BookingResponse>()

        val response = client.cancelBooking(other.accessToken, club.id, booking.id)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `admin blocks a slot, it occupies the grid and members can't book over it`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val startsAt = slotStart(1, 10)

        val block = client.createBlock(ownerToken, club.id, court.id, startsAt, note = "Platzpflege")
        assertEquals(HttpStatusCode.Created, block.status)
        val body = block.body<BookingResponse>()
        assertEquals(BookingStatus.BLOCKED, body.status)
        assertNull(body.userId)
        assertEquals(0, body.finalPriceCents)
        assertEquals("Platzpflege", body.note)

        val date = startsAt.toLocalDateTime(timeZone).date.toString()
        val day = client.getSlots(ownerToken, club.id, court.id, date).body<DayAvailabilityResponse>()
        assertTrue(day.slots.first { it.startsAt == startsAt }.available.not())

        val bookOverBlock = client.createBooking(ownerToken, club.id, court.id, startsAt)
        assertEquals(HttpStatusCode.Conflict, bookOverBlock.status)
        assertEquals(ErrorCode.BOOKING_CONFLICT, bookOverBlock.body<ApiError>().code)
    }

    @Test
    fun `a plain member cannot block a slot`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)

        val response = client.createBlock(member.accessToken, club.id, court.id, slotStart(1, 10))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin un-blocks a slot, it becomes bookable again`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val startsAt = slotStart(1, 10)
        val block = client.createBlock(ownerToken, club.id, court.id, startsAt).body<BookingResponse>()

        val unblocked = client.cancelBooking(ownerToken, club.id, block.id)
        assertEquals(HttpStatusCode.NoContent, unblocked.status)

        val date = startsAt.toLocalDateTime(timeZone).date.toString()
        val day = client.getSlots(ownerToken, club.id, court.id, date).body<DayAvailabilityResponse>()
        assertTrue(day.slots.first { it.startsAt == startsAt }.available)
    }

    @Test
    fun `admin cancels a member's booking, bypassing the cancellation window and waiving a DUE fee`() =
        authTestApp { client ->
            val (club, ownerToken, court) = client.newClubWithCourt(defaultPriceCents = 1200, memberDiscountPct = 0)
            val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
            val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
            client.decideApplication(ownerToken, club.id, application.id, approve = true)
            val memberId = client.me(member.accessToken).body<UserResponse>().id
            val soon = Clock.System.now() + 1.hours
            // Bypasses BookingService.create (which would reject a same-hour start) — this just
            // needs a DUE booking inside the cancellation window for an admin to override.
            val bookingId = insertBooking(
                club.id,
                court.id,
                soon.toString(),
                (soon + 1.hours).toString(),
                userId = memberId,
                paymentStatus = "DUE",
            )

            val response = client.cancelBooking(ownerToken, club.id, bookingId)
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("CANCELLED" to "WAIVED", bookingState(bookingId))
        }

    @Test
    fun `admin marks a booking's fee paid`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt(defaultPriceCents = 1200, memberDiscountPct = 0)
        val guest = client.register(uniqueEmail("guest")).body<TokenPairResponse>()
        val booking = client.createBooking(guest.accessToken, club.id, court.id, slotStart(1, 10)).body<BookingResponse>()
        assertEquals(PaymentStatus.DUE, booking.paymentStatus)

        val response = client.updateBookingPayment(ownerToken, club.id, booking.id, PaymentStatus.PAID)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(PaymentStatus.PAID, response.body<BookingResponse>().paymentStatus)
    }

    @Test
    fun `a non-admin cannot mark a booking's payment`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val booking = client.createBooking(ownerToken, club.id, court.id, slotStart(1, 10)).body<BookingResponse>()

        val response = client.updateBookingPayment(ownerToken, club.id, booking.id, PaymentStatus.PAID)
        // ownerToken IS an admin here — use a plain member instead to prove the gate is real.
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val application = client.applyToClub(member.accessToken, club.id).body<MembershipResponse>()
        client.decideApplication(ownerToken, club.id, application.id, approve = true)

        val memberResponse = client.updateBookingPayment(member.accessToken, club.id, booking.id, PaymentStatus.PAID)
        assertEquals(HttpStatusCode.Forbidden, memberResponse.status)
        assertEquals(HttpStatusCode.OK, response.status) // sanity: the earlier admin call did succeed
    }

    @Test
    fun `marking a block's payment 404s - a block carries no fee`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt()
        val block = client.createBlock(ownerToken, club.id, court.id, slotStart(1, 10)).body<BookingResponse>()

        val response = client.updateBookingPayment(ownerToken, club.id, block.id, PaymentStatus.PAID)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `marking payment NONE is rejected - only PAID or WAIVED are valid admin actions`() = authTestApp { client ->
        val (club, ownerToken, court) = client.newClubWithCourt(defaultPriceCents = 1200, memberDiscountPct = 0)
        val booking = client.createBooking(ownerToken, club.id, court.id, slotStart(1, 10)).body<BookingResponse>()

        val response = client.updateBookingPayment(ownerToken, club.id, booking.id, PaymentStatus.NONE)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ApiError>().code)
    }

    @Test
    fun `a member of club A cannot book against club B's court by guessing its id`() = authTestApp { client ->
        val (clubA, ownerAToken, _) = client.newClubWithCourt()
        val (_, _, courtB) = client.newClubWithCourt()

        // Under clubA's own path, but naming courtB's id — the court lookup is scoped to
        // (courtId, clubA), so it 404s rather than reaching across into clubB.
        val response = client.createBooking(ownerAToken, clubA.id, courtB.id, slotStart(1, 10))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `a member of club A cannot cancel club B's booking through club A's path`() = authTestApp { client ->
        val (clubA, ownerAToken, _) = client.newClubWithCourt()
        val (clubB, ownerBToken, courtB) = client.newClubWithCourt()
        val bookingB = client.createBooking(ownerBToken, clubB.id, courtB.id, slotStart(1, 10)).body<BookingResponse>()

        // ownerA is an ADMIN of clubA, but that grants no authority over clubB's booking —
        // findByIdAndClub is scoped to clubA, so bookingB's id simply isn't found there.
        val response = client.cancelBooking(ownerAToken, clubA.id, bookingB.id)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `me-bookings lists only upcoming active bookings, soonest first, across clubs`() = authTestApp { client ->
        val (clubA, ownerAToken, courtA) = client.newClubWithCourt()
        val (clubB, ownerBToken, courtB) = client.newClubWithCourt()
        val member = client.register(uniqueEmail("member")).body<TokenPairResponse>()
        val applicationA = client.applyToClub(member.accessToken, clubA.id).body<MembershipResponse>()
        client.decideApplication(ownerAToken, clubA.id, applicationA.id, approve = true)
        val applicationB = client.applyToClub(member.accessToken, clubB.id).body<MembershipResponse>()
        client.decideApplication(ownerBToken, clubB.id, applicationB.id, approve = true)

        val laterBooking = client.createBooking(member.accessToken, clubA.id, courtA.id, slotStart(3, 10))
            .body<BookingResponse>()
        val soonerBooking = client.createBooking(member.accessToken, clubB.id, courtB.id, slotStart(1, 10))
            .body<BookingResponse>()
        val cancelledBooking = client.createBooking(member.accessToken, clubA.id, courtA.id, slotStart(2, 10))
            .body<BookingResponse>()
        assertEquals(HttpStatusCode.NoContent, client.cancelBooking(member.accessToken, clubA.id, cancelledBooking.id).status)

        val response = client.myBookings(member.accessToken)
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.body<Page<MyBookingResponse>>()
        assertEquals(listOf(soonerBooking.id, laterBooking.id), page.items.map { it.booking.id })
        assertEquals(clubB.name, page.items[0].clubName)
        assertEquals(courtB.name, page.items[0].courtName)
    }
}
