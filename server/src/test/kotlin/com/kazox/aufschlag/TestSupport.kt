package com.kazox.aufschlag

import com.kazox.aufschlag.api.auth.DeleteAccountRequest
import com.kazox.aufschlag.api.auth.LoginRequest
import com.kazox.aufschlag.api.auth.LogoutRequest
import com.kazox.aufschlag.api.auth.PasswordResetConfirmRequest
import com.kazox.aufschlag.api.auth.PasswordResetRequest
import com.kazox.aufschlag.api.auth.RefreshRequest
import com.kazox.aufschlag.api.auth.RegisterRequest
import com.kazox.aufschlag.api.booking.CreateBlockRequest
import com.kazox.aufschlag.api.booking.CreateBookingRequest
import com.kazox.aufschlag.api.booking.PaymentStatus
import com.kazox.aufschlag.api.booking.UpdateBookingPaymentRequest
import com.kazox.aufschlag.api.club.ApplyToClubRequest
import com.kazox.aufschlag.api.club.CreateClubRequest
import com.kazox.aufschlag.api.club.MembershipDecisionRequest
import com.kazox.aufschlag.api.club.MembershipRole
import com.kazox.aufschlag.api.club.MembershipStatus
import com.kazox.aufschlag.api.club.UpdateClubProfileRequest
import com.kazox.aufschlag.api.club.UpdateMemberRequest
import com.kazox.aufschlag.api.court.CourtSurface
import com.kazox.aufschlag.api.court.CreateCourtRequest
import com.kazox.aufschlag.api.court.ReplacePriceRulesRequest
import com.kazox.aufschlag.api.court.UpdateCourtRequest
import com.kazox.aufschlag.config.AuthConfig
import com.kazox.aufschlag.mail.LoggingMailer
import com.kazox.aufschlag.mail.Mailer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A slot start [daysFromToday] days out at club-local [hour]:00 — dynamic rather than a fixed
 *  calendar date so advance-booking-window and cancellation-window tests stay meaningful
 *  regardless of when the suite runs. */
fun dynamicSlotStart(timeZone: TimeZone, daysFromToday: Int, hour: Int): Instant {
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    val date = today.plus(daysFromToday, DateTimeUnit.DAY)
    return LocalDateTime(date, LocalTime(hour, 0)).toInstant(timeZone)
}

class RecordingMailer : Mailer {
    data class Sent(val to: String, val token: String)
    data class DecisionSent(val to: String, val clubName: String, val approved: Boolean)

    val sent = ConcurrentLinkedQueue<Sent>()
    val decisions = ConcurrentLinkedQueue<DecisionSent>()
    private val notifications = Channel<Sent>(Channel.UNLIMITED)
    private val decisionNotifications = Channel<DecisionSent>(Channel.UNLIMITED)

    override suspend fun sendPasswordReset(to: String, resetToken: String) {
        val mail = Sent(to, resetToken)
        sent += mail
        notifications.trySend(mail)
    }

    override suspend fun sendApplicationDecision(to: String, clubName: String, approved: Boolean) {
        val mail = DecisionSent(to, clubName, approved)
        decisions += mail
        decisionNotifications.trySend(mail)
    }

    /** Sends are fire-and-forget in AuthService, so tests must await, not poll [sent]. */
    suspend fun awaitTokenFor(email: String): String {
        sent.lastOrNull { it.to == email }?.let { return it.token }
        while (true) {
            val mail = notifications.receive()
            if (mail.to == email) return mail.token
        }
    }

    /** Sends are fire-and-forget in MembershipService, so tests must await, not poll [decisions]. */
    suspend fun awaitDecisionFor(email: String): DecisionSent {
        decisions.lastOrNull { it.to == email }?.let { return it }
        while (true) {
            val mail = decisionNotifications.receive()
            if (mail.to == email) return mail
        }
    }
}

fun authTestApp(
    auth: AuthConfig = testAuthConfig,
    mailer: Mailer = LoggingMailer(),
    trustProxyHeaders: Boolean = false,
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    application {
        module(TestDatabase.dataSource, TestDatabase.database, auth, mailer, trustProxyHeaders = trustProxyHeaders)
    }
    val client = createClient {
        install(ContentNegotiation) { json() }
    }
    block(client)
}

/** Tests share one database, so every test uses its own unique email. */
fun uniqueEmail(prefix: String = "user"): String = "$prefix-${Uuid.random()}@example.test"

/** Tests share one database, so every test uses its own unique club slug. */
fun uniqueSlug(prefix: String = "club"): String = "$prefix-${Uuid.random()}"

suspend fun HttpClient.register(
    email: String,
    password: String = "password-123",
    name: String = "Test User",
): HttpResponse = post("/v1/auth/register") {
    contentType(ContentType.Application.Json)
    setBody(RegisterRequest(email, password, name))
}

suspend fun HttpClient.login(email: String, password: String, forwardedFor: String? = null): HttpResponse =
    post("/v1/auth/login") {
        contentType(ContentType.Application.Json)
        if (forwardedFor != null) header(HttpHeaders.XForwardedFor, forwardedFor)
        setBody(LoginRequest(email, password))
    }

suspend fun HttpClient.refresh(refreshToken: String): HttpResponse =
    post("/v1/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(refreshToken))
    }

suspend fun HttpClient.logout(refreshToken: String): HttpResponse =
    post("/v1/auth/logout") {
        contentType(ContentType.Application.Json)
        setBody(LogoutRequest(refreshToken))
    }

suspend fun HttpClient.requestPasswordReset(email: String): HttpResponse =
    post("/v1/auth/password-reset/request") {
        contentType(ContentType.Application.Json)
        setBody(PasswordResetRequest(email))
    }

suspend fun HttpClient.confirmPasswordReset(token: String, newPassword: String): HttpResponse =
    post("/v1/auth/password-reset/confirm") {
        contentType(ContentType.Application.Json)
        setBody(PasswordResetConfirmRequest(token, newPassword))
    }

suspend fun HttpClient.me(accessToken: String?): HttpResponse =
    get("/v1/me") {
        if (accessToken != null) bearerAuth(accessToken)
    }

suspend fun HttpClient.myMemberships(accessToken: String, limit: Int? = null, cursor: String? = null): HttpResponse =
    get("/v1/me/memberships") {
        bearerAuth(accessToken)
        if (limit != null) parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }

suspend fun HttpClient.deleteAccount(accessToken: String, password: String): HttpResponse =
    delete("/v1/me") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(DeleteAccountRequest(password))
    }

suspend fun HttpClient.createClub(
    accessToken: String,
    name: String,
    slug: String,
    ownerEmail: String,
    timezone: String = "Europe/Berlin",
): HttpResponse =
    post("/v1/clubs") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(CreateClubRequest(name = name, slug = slug, ownerEmail = ownerEmail, timezone = timezone))
    }

suspend fun HttpClient.searchClubs(query: String? = null, limit: Int? = null, cursor: String? = null): HttpResponse =
    get("/v1/clubs") {
        if (query != null) parameter("search", query)
        if (limit != null) parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }

suspend fun HttpClient.getClub(accessToken: String, clubId: String): HttpResponse =
    get("/v1/clubs/$clubId") { bearerAuth(accessToken) }

suspend fun HttpClient.updateClubProfile(
    accessToken: String,
    clubId: String,
    request: UpdateClubProfileRequest,
): HttpResponse =
    put("/v1/clubs/$clubId") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

suspend fun HttpClient.applyToClub(accessToken: String, clubId: String): HttpResponse =
    post("/v1/clubs/$clubId/applications") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(ApplyToClubRequest())
    }

suspend fun HttpClient.listApplications(accessToken: String, clubId: String): HttpResponse =
    get("/v1/clubs/$clubId/applications") { bearerAuth(accessToken) }

suspend fun HttpClient.decideApplication(
    accessToken: String,
    clubId: String,
    applicationId: String,
    approve: Boolean,
): HttpResponse =
    put("/v1/clubs/$clubId/applications/$applicationId") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(MembershipDecisionRequest(approve))
    }

suspend fun HttpClient.listMembers(accessToken: String, clubId: String): HttpResponse =
    get("/v1/clubs/$clubId/members") { bearerAuth(accessToken) }

suspend fun HttpClient.updateMember(
    accessToken: String,
    clubId: String,
    membershipId: String,
    role: MembershipRole? = null,
    status: MembershipStatus? = null,
): HttpResponse = put("/v1/clubs/$clubId/members/$membershipId") {
    bearerAuth(accessToken)
    contentType(ContentType.Application.Json)
    setBody(UpdateMemberRequest(role = role, status = status))
}

suspend fun HttpClient.pauseMembership(accessToken: String, clubId: String): HttpResponse =
    post("/v1/clubs/$clubId/membership/pause") { bearerAuth(accessToken) }

suspend fun HttpClient.resumeMembership(accessToken: String, clubId: String): HttpResponse =
    post("/v1/clubs/$clubId/membership/resume") { bearerAuth(accessToken) }

suspend fun HttpClient.createCourt(
    accessToken: String,
    clubId: String,
    name: String = "Court 1",
    surface: CourtSurface = CourtSurface.CLAY,
    indoor: Boolean = false,
    slotMinutes: Int = 60,
    defaultPriceCents: Int = 1200,
    memberDiscountPct: Int = 100,
    pausedDiscountPct: Int = 0,
): HttpResponse =
    post("/v1/clubs/$clubId/courts") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(
            CreateCourtRequest(
                name = name,
                surface = surface,
                indoor = indoor,
                slotMinutes = slotMinutes,
                defaultPriceCents = defaultPriceCents,
                memberDiscountPct = memberDiscountPct,
                pausedDiscountPct = pausedDiscountPct,
            ),
        )
    }

suspend fun HttpClient.updateCourt(
    accessToken: String,
    clubId: String,
    courtId: String,
    request: UpdateCourtRequest,
): HttpResponse =
    put("/v1/clubs/$clubId/courts/$courtId") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

suspend fun HttpClient.listCourts(accessToken: String, clubId: String, limit: Int? = null, cursor: String? = null): HttpResponse =
    get("/v1/clubs/$clubId/courts") {
        bearerAuth(accessToken)
        if (limit != null) parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }

suspend fun HttpClient.replacePriceRules(
    accessToken: String,
    clubId: String,
    courtId: String,
    request: ReplacePriceRulesRequest,
): HttpResponse =
    put("/v1/clubs/$clubId/courts/$courtId/pricing") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

suspend fun HttpClient.listPriceRules(accessToken: String, clubId: String, courtId: String): HttpResponse =
    get("/v1/clubs/$clubId/courts/$courtId/pricing") { bearerAuth(accessToken) }

suspend fun HttpClient.getSlots(accessToken: String, clubId: String, courtId: String, date: String): HttpResponse =
    get("/v1/clubs/$clubId/courts/$courtId/slots") {
        bearerAuth(accessToken)
        parameter("date", date)
    }

suspend fun HttpClient.courtDayBookings(accessToken: String, clubId: String, courtId: String, date: String): HttpResponse =
    get("/v1/clubs/$clubId/courts/$courtId/bookings") {
        bearerAuth(accessToken)
        parameter("date", date)
    }

suspend fun HttpClient.createBooking(
    accessToken: String,
    clubId: String,
    courtId: String,
    startsAt: kotlinx.datetime.Instant,
): HttpResponse =
    post("/v1/clubs/$clubId/bookings") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(CreateBookingRequest(courtId, startsAt))
    }

suspend fun HttpClient.cancelBooking(accessToken: String, clubId: String, bookingId: String): HttpResponse =
    delete("/v1/clubs/$clubId/bookings/$bookingId") { bearerAuth(accessToken) }

suspend fun HttpClient.createBlock(
    accessToken: String,
    clubId: String,
    courtId: String,
    startsAt: kotlinx.datetime.Instant,
    note: String? = null,
): HttpResponse =
    post("/v1/clubs/$clubId/blocks") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(CreateBlockRequest(courtId, startsAt, note))
    }

suspend fun HttpClient.updateBookingPayment(
    accessToken: String,
    clubId: String,
    bookingId: String,
    paymentStatus: PaymentStatus,
): HttpResponse =
    put("/v1/clubs/$clubId/bookings/$bookingId/payment") {
        bearerAuth(accessToken)
        contentType(ContentType.Application.Json)
        setBody(UpdateBookingPaymentRequest(paymentStatus))
    }

suspend fun HttpClient.myBookings(accessToken: String, limit: Int? = null, cursor: String? = null): HttpResponse =
    get("/v1/me/bookings") {
        bearerAuth(accessToken)
        if (limit != null) parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }

fun setClubStatus(clubId: String, status: String) {
    TestDatabase.dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE clubs SET status = ? WHERE id = ?::uuid").use {
            it.setString(1, status)
            it.setString(2, clubId)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun endMembership(membershipId: String) {
    TestDatabase.dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE memberships SET status = 'ENDED' WHERE id = ?::uuid").use {
            it.setString(1, membershipId)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun setClubSettings(clubId: String, settingsJson: String) {
    TestDatabase.dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE clubs SET settings = ?::jsonb WHERE id = ?::uuid").use {
            it.setString(1, settingsJson)
            it.setString(2, clubId)
            it.executeUpdate()
        }
        connection.commit()
    }
}

/** Inserts a booking row directly, bypassing BookingService's window/eligibility checks — used
 *  to set up fixtures those checks would otherwise reject (e.g. a booking starting in an hour,
 *  for a cancellation-window test). [startsAt]/[endsAt] are ISO-8601 instants
 *  (e.g. "2026-08-03T07:00:00Z"). */
fun insertBooking(
    clubId: String,
    courtId: String,
    startsAt: String,
    endsAt: String,
    status: String = "ACTIVE",
    userId: String? = null,
    paymentStatus: String = "NONE",
): String {
    val id = Uuid.random().toString()
    TestDatabase.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO bookings (
                id, club_id, court_id, user_id, starts_at, ends_at, status,
                base_price_cents, discount_pct, final_price_cents, payment_status
            ) VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::timestamptz, ?::timestamptz, ?, 0, 0, 0, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, clubId)
            statement.setString(3, courtId)
            statement.setString(4, userId)
            statement.setString(5, startsAt)
            statement.setString(6, endsAt)
            statement.setString(7, status)
            statement.setString(8, paymentStatus)
            statement.executeUpdate()
        }
        connection.commit()
    }
    return id
}

/** (status, paymentStatus) of a booking row, read directly — there's no GET /bookings/{id} yet. */
fun bookingState(bookingId: String): Pair<String, String> {
    TestDatabase.dataSource.connection.use { connection ->
        val state = connection.prepareStatement(
            "SELECT status, payment_status FROM bookings WHERE id = ?::uuid",
        ).use { statement ->
            statement.setString(1, bookingId)
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getString(1) to rs.getString(2)
            }
        }
        connection.rollback()
        return state
    }
}

/** Number of membership rows (incl. ENDED history) for a (user, club) pair. */
fun membershipRowCount(userId: String, clubId: String): Int {
    TestDatabase.dataSource.connection.use { connection ->
        val count = connection.prepareStatement(
            "SELECT count(*) FROM memberships WHERE user_id = ?::uuid AND club_id = ?::uuid",
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, clubId)
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        connection.rollback()
        return count
    }
}

/** Number of refresh-token rows in the family that [rawRefreshToken] belongs to. */
fun refreshTokenFamilySize(rawRefreshToken: String): Int {
    val hash = com.kazox.aufschlag.security.Tokens.sha256(rawRefreshToken)
    TestDatabase.dataSource.connection.use { connection ->
        val count = connection.prepareStatement(
            """
            SELECT count(*) FROM refresh_tokens
            WHERE family_id = (SELECT family_id FROM refresh_tokens WHERE token_hash = ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, hash)
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        connection.rollback()
        return count
    }
}
