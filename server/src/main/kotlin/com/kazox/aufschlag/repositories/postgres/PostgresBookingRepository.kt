package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.BookingsTable
import com.kazox.aufschlag.db.ClubsTable
import com.kazox.aufschlag.db.CourtsTable
import com.kazox.aufschlag.db.UsersTable
import com.kazox.aufschlag.repositories.BookingRepository
import com.kazox.aufschlag.repositories.BookingRow
import com.kazox.aufschlag.repositories.BookingWithNamesRow
import com.kazox.aufschlag.repositories.BookingWithUserRow
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
                    (BookingsTable.status inList listOf(STATUS_ACTIVE, STATUS_BLOCKED)) and
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
                    (BookingsTable.status inList listOf(STATUS_ACTIVE, STATUS_BLOCKED)) and
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
        status: String,
        note: String?,
        basePriceCents: Int,
        discountPct: Int,
        finalPriceCents: Int,
        paymentStatus: String,
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
                    (BookingsTable.status eq STATUS_ACTIVE) and (BookingsTable.endsAt greater now)
            }
            .count()
            .toInt()

    override fun cancel(id: Uuid, clubId: Uuid, ownerId: Uuid, paymentStatus: String): Int =
        BookingsTable.update({
            (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) and
                (BookingsTable.userId eq ownerId) and (BookingsTable.status eq STATUS_ACTIVE)
        }) {
            it[BookingsTable.status] = STATUS_CANCELLED
            it[BookingsTable.paymentStatus] = paymentStatus
        }

    override fun cancelAny(id: Uuid, clubId: Uuid, paymentStatus: String): Int =
        BookingsTable.update({
            (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) and
                (BookingsTable.status inList listOf(STATUS_ACTIVE, STATUS_BLOCKED))
        }) {
            it[BookingsTable.status] = STATUS_CANCELLED
            it[BookingsTable.paymentStatus] = paymentStatus
        }

    override fun updatePayment(id: Uuid, clubId: Uuid, paymentStatus: String): Int =
        BookingsTable.update({
            (BookingsTable.id eq id) and (BookingsTable.clubId eq clubId) and (BookingsTable.userId.isNotNull())
        }) {
            it[BookingsTable.paymentStatus] = paymentStatus
        }

    override fun cancelAllFutureForUser(userId: Uuid, now: Instant): Int {
        val waived = BookingsTable.update({
            (BookingsTable.userId eq userId) and (BookingsTable.status eq STATUS_ACTIVE) and
                (BookingsTable.endsAt greater now) and (BookingsTable.paymentStatus eq PAYMENT_DUE)
        }) {
            it[BookingsTable.status] = STATUS_CANCELLED
            it[BookingsTable.paymentStatus] = PAYMENT_WAIVED
        }
        val others = BookingsTable.update({
            (BookingsTable.userId eq userId) and (BookingsTable.status eq STATUS_ACTIVE) and
                (BookingsTable.endsAt greater now) and (BookingsTable.paymentStatus neq PAYMENT_DUE)
        }) {
            it[BookingsTable.status] = STATUS_CANCELLED
        }
        return waived + others
    }

    override fun listUpcomingForUser(
        userId: Uuid,
        now: Instant,
        limit: Int,
        cursorStartsAt: Instant?,
        cursorId: Uuid?,
    ): List<BookingWithNamesRow> =
        BookingsTable
            .innerJoin(CourtsTable) { BookingsTable.courtId eq CourtsTable.id }
            .innerJoin(ClubsTable) { BookingsTable.clubId eq ClubsTable.id }
            .selectAll()
            .where {
                val ownerCondition = (BookingsTable.userId eq userId) and
                    (BookingsTable.status eq STATUS_ACTIVE) and
                    (BookingsTable.endsAt greater now)
                val cursorCondition = if (cursorStartsAt != null && cursorId != null) {
                    (BookingsTable.startsAt greater cursorStartsAt) or
                        ((BookingsTable.startsAt eq cursorStartsAt) and (BookingsTable.id greater cursorId))
                } else {
                    Op.TRUE
                }
                ownerCondition and cursorCondition
            }
            .orderBy(BookingsTable.startsAt to SortOrder.ASC, BookingsTable.id to SortOrder.ASC)
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

    companion object {
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val STATUS_BLOCKED = "BLOCKED"
        private const val STATUS_CANCELLED = "CANCELLED"
        private const val PAYMENT_DUE = "DUE"
        private const val PAYMENT_WAIVED = "WAIVED"
    }
}
