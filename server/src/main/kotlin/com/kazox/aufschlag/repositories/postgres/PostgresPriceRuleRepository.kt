package com.kazox.aufschlag.repositories.postgres

import com.kazox.aufschlag.db.PriceRulesTable
import com.kazox.aufschlag.repositories.NewPriceRule
import com.kazox.aufschlag.repositories.PriceRuleRepository
import com.kazox.aufschlag.repositories.PriceRuleRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.uuid.Uuid

class PostgresPriceRuleRepository : PriceRuleRepository {

    override fun findByCourt(courtId: Uuid): List<PriceRuleRow> =
        PriceRulesTable.selectAll()
            .where { PriceRulesTable.courtId eq courtId }
            .map { it.toPriceRuleRow() }

    override fun replaceAll(courtId: Uuid, rules: List<NewPriceRule>) {
        PriceRulesTable.deleteWhere { PriceRulesTable.courtId eq courtId }
        rules.forEach { rule ->
            PriceRulesTable.insert {
                it[id] = Uuid.random()
                it[PriceRulesTable.courtId] = courtId
                it[daysOfWeek] = rule.daysOfWeekJson
                it[startTime] = rule.startTime
                it[endTime] = rule.endTime
                it[validFrom] = rule.validFrom
                it[validTo] = rule.validTo
                it[priceCents] = rule.priceCents
            }
        }
    }

    override fun lockForReplace(courtId: Uuid) {
        TransactionManager.current().exec(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
            listOf(TextColumnType() to "$courtId"),
        )
    }

    private fun ResultRow.toPriceRuleRow() = PriceRuleRow(
        id = this[PriceRulesTable.id],
        courtId = this[PriceRulesTable.courtId],
        daysOfWeekJson = this[PriceRulesTable.daysOfWeek],
        startTime = this[PriceRulesTable.startTime],
        endTime = this[PriceRulesTable.endTime],
        validFrom = this[PriceRulesTable.validFrom],
        validTo = this[PriceRulesTable.validTo],
        priceCents = this[PriceRulesTable.priceCents],
    )
}
