package com.kazox.aufschlag.services

import com.kazox.aufschlag.api.club.ClubSettings
import com.kazox.aufschlag.repositories.ClubRow
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

internal fun ClubRow.decodeSettings(): ClubSettings = json.decodeFromString(ClubSettings.serializer(), settingsJson)
