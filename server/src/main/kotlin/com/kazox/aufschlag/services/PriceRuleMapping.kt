package com.kazox.aufschlag.services

import com.kazox.aufschlag.api.court.PriceRuleResponse
import com.kazox.aufschlag.pricing.PriceRule
import com.kazox.aufschlag.pricing.dayOfWeekFromIsoNumber
import com.kazox.aufschlag.repositories.PriceRuleRow
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val intListSerializer = ListSerializer(Int.serializer())

internal fun encodeDaysOfWeekJson(isoDayNumbers: List<Int>): String =
    json.encodeToString(intListSerializer, isoDayNumbers.sorted())

private fun PriceRuleRow.decodeDaysOfWeek() =
    json.decodeFromString(intListSerializer, daysOfWeekJson).map(::dayOfWeekFromIsoNumber).toSet()

/** Row → the pure engine value [matchPriceRule][com.kazox.aufschlag.pricing.matchPriceRule] matches against. */
internal fun PriceRuleRow.toEngineRule() = PriceRule(
    daysOfWeek = decodeDaysOfWeek(),
    startTime = startTime.toKotlinLocalTime(),
    endTime = endTime.toKotlinLocalTime(),
    validFrom = validFrom?.toKotlinLocalDate(),
    validTo = validTo?.toKotlinLocalDate(),
    priceCents = priceCents,
)

internal fun PriceRuleRow.toResponse() = PriceRuleResponse(
    id = id.toString(),
    daysOfWeek = decodeDaysOfWeek(),
    startTime = startTime.toKotlinLocalTime(),
    endTime = endTime.toKotlinLocalTime(),
    validFrom = validFrom?.toKotlinLocalDate(),
    validTo = validTo?.toKotlinLocalDate(),
    priceCents = priceCents,
)
