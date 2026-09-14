package com.kazox.aufschlag.api.court

import kotlinx.serialization.Serializable

@Serializable
enum class CourtSurface { CLAY, HARD, GRASS, CARPET, ARTIFICIAL_TURF }

@Serializable
data class CreateCourtRequest(
    val name: String,
    val surface: CourtSurface,
    val indoor: Boolean = false,
    val slotMinutes: Int = 60,
    val defaultPriceCents: Int = 0,
    val memberDiscountPct: Int = 100,
    val pausedDiscountPct: Int = 0,
)

@Serializable
data class UpdateCourtRequest(
    val name: String,
    val surface: CourtSurface,
    val indoor: Boolean,
    val active: Boolean,
    val slotMinutes: Int,
    val defaultPriceCents: Int,
    val memberDiscountPct: Int,
    val pausedDiscountPct: Int,
)

@Serializable
data class CourtResponse(
    val id: String,
    val clubId: String,
    val name: String,
    val surface: CourtSurface,
    val indoor: Boolean,
    val active: Boolean,
    val slotMinutes: Int,
    val defaultPriceCents: Int,
    val memberDiscountPct: Int,
    val pausedDiscountPct: Int,
)
