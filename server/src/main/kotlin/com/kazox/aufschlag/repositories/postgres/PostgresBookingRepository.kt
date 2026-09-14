package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.api.booking.BookingStatus
import com.kazox.aufschlag.api.booking.PaymentStatus
import com.kazox.aufschlag.db.BookingsTable
import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.db.CourtsTable
import com.kazox.aufschlag.db.UsersTable
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.BookingRow
import com.kazox.aufschlag.repositories.BookingWithNamesRow
import com.kazox.aufschlag.repositories.BookingWithUserRow
import com.kazox.aufschlag.repositories.Keyset
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid

class PostgresBookingRepository : BookingRepository {

    override fun findOccupyingInRange(courtId: Uuid, from: Instant, to: Instant): List<BookingRow> =
        BookingsTable.selectAll()
            .where {
                (BookingsTable.courtId eq courtId) and
                    (BookingsTable.status inList listOf(BookingStatus.ACTIVE, BookingStatus.BLOCKED)) and
                    (BookingsTable.startsAt less to) and
                    (BookingsTable.endsAt greater from)
            }
            .map { it.toBookingRow() }

    override fun findOccupyingInRangeWithUser(courtId: Uuid, from: Instant, to: Instant): List<BookingWithUserRow> =
        BookingsTable
            .join(UsersTable, JoinType.LEFT) { BookingsTable.userId eq UsersTable.id }
            .selectAll()
            .where {
                (BookingsTable.courtId eq courtId) and
                    (BookingsTable.status inList listOf(BookingStatus.ACTIVE, BookingStatus.BLOCKED)) and
                    (BookingsTable.startsAt less to) and
                    (BookingsTable.endsAt greater from)
            }
            .orderBy(BookingsTable.startsAt to SortOrder.ASC)
            .map { BookingWithUserRow(booking = it.toBookingRow(), userName = it.getOrNull(UsersTable.name)) }

    override fun create(
        clubId: Uuid,
        courtId: Uuid,
        userId: Uuid?,
        startsAt: Instant,
        endsAt: Instant,
        status: BookingStatus,
        note: String?,
        basePriceCents: Int,
        discountPct: Int,
        finalPriceCents: Int,
        paymentStatus: PaymentStatus,
    ): Uuid {
        val id = Uuid.random()
        BookingsTable.insert {
            it[BookingsTable.id] = id
            it[BookingsTable.clubId] = clubId
            it[BookingsTable.courtId] = courtId
            it[BookingsTable.userId] = userId
            it[BookingsTable.startsAt] = startsAt
            it[BookingsTable.endsAt] = endsAt
            it[BookingsTable.status] = status
            it[BookingsTable.note] = note
            it[BookingsTable.basePriceCents] = basePriceCents
            it[BookingsTable.discountPct] = discountPct
            it[BookingsTable.finalPriceCents] = finalPriceCents
            it[BookingsTable.paymentStatus] = paymentStatus
        }
        return id
    }

    override fun findByIdAndClub(id: Uuid, clubId: Uuid): BookingRow? =
        BookingsTable.selectAll()
            .where { (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) }
            .singleOrNull()
            ?.toBookingRow()

    override fun countOpenForUser(userId: Uuid, clubId: Uuid, now: Instant): Int =
        BookingsTable.selectAll()
            .where {
                (BookingsTable.userId eq userId) and (BookingsTable.clubId eq clubId) and
                    (BookingsTable.status eq BookingStatus.ACTIVE) and (BookingsTable.endsAt greater now)
            }
            .count()
            .toInt()

    override fun cancel(id: Uuid, clubId: Uuid, ownerId: Uuid, paymentStatus: PaymentStatus): Int =
        BookingsTable.update({
            (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) and
                (BookingsTable.userId eq ownerId) and (BookingsTable.status eq BookingStatus.ACTIVE)
        }) {
            it[BookingsTable.status] = BookingStatus.CANCELLED
            it[BookingsTable.paymentStatus] = paymentStatus
        }

    override fun cancelAny(id: Uuid, clubId: Uuid, paymentStatus: PaymentStatus): Int =
        BookingsTable.update({
            (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) and
                (BookingsTable.status inList listOf(BookingStatus.ACTIVE, BookingStatus.BLOCKED))
        }) {
            it[BookingsTable.status] = BookingStatus.CANCELLED
            it[BookingsTable.paymentStatus] = paymentStatus
        }

    override fun updatePayment(id: Uuid, clubId: Uuid, paymentStatus: PaymentStatus): Int =
        BookingsTable.update({
            (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) and (BookingsTable.userId.isNotNull())
        }) {
            it[BookingsTable.paymentStatus] = paymentStatus
        }

    override fun cancelAllFutureForUser(userId: Uuid, now: Instant): Int {
        val waived = BookingsTable.update({
            (BookingsTable.userId eq userId) and (BookingsTable.status eq BookingStatus.ACTIVE) and
                (BookingsTable.endsAt greater now) and (BookingsTable.paymentStatus eq PaymentStatus.DUE)
        }) {
            it[BookingsTable.status] = BookingStatus.CANCELLED
            it[BookingsTable.paymentStatus] = PaymentStatus.WAIVED
        }
        val others = BookingsTable.update({
            (BookingsTable.userId eq userId) and (BookingsTable.status eq BookingStatus.ACTIVE) and
                (BookingsTable.endsAt greater now) and (BookingsTable.paymentStatus neq PaymentStatus.DUE)
        }) {
            it[BookingsTable.status] = BookingStatus.CANCELLED
        }
        return waived + others
    }

    override fun listUpcomingForUser(userId: Uuid, now: Instant, limit: Int, cursor: Keyset?): List<BookingWithNamesRow> =
        BookingsTable
            .innerJoin(CourtsTable) { BookingsTable.courtId eq CourtsTable.id }
            .innerJoin(ClubsTable) { BookingsTable.clubId eq ClubsTable.id }
            .selectAll()
            .where {
                val ownerCondition = (BookingsTable.userId eq userId) and
                    (BookingsTable.status eq BookingStatus.ACTIVE) and
                    (BookingsTable.endsAt greater now)
                ownerCondition and keysetAfter(BookingsTable.startsAt, BookingsTable.id, cursor)
            }
            .orderBy(*keysetOrder(BookingsTable.startsAt, BookingsTable.id))
            .limit(limit)
            .map {
                BookingWithNamesRow(
                    booking = it.toBookingRow(),
                    clubName = it[ClubsTable.name],
                    clubTimezone = it[ClubsTable.timezone],
                    courtName = it[CourtsTable.name],
                )
            }

    override fun lockForBookingLimitCheck(userId: Uuid) {
        TransactionManager.current().exec(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
            listOf(TextColumnType() to "$userId"),
        )
    }

    private fun ResultRow.toBookingRow() = BookingRow(
        id = this[BookingsTable.id],
        clubId = this[BookingsTable.clubId],
        courtId = this[BookingsTable.courtId],
        userId = this[BookingsTable.userId],
        startsAt = this[BookingsTable.startsAt],
        endsAt = this[BookingsTable.endsAt],
        status = this[BookingsTable.status],
        note = this[BookingsTable.note],
        basePriceCents = this[BookingsTable.basePriceCents],
        discountPct = this[BookingsTable.discountPct],
        finalPriceCents = this[BookingsTable.finalPriceCents],
        paymentStatus = this[BookingsTable.paymentStatus],
    )
}
