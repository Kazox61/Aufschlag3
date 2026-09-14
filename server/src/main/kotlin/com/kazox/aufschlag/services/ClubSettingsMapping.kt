package com.kazox.aufschlag.services

import com.kazox.aufschlag.AppJson
import com.kazox.aufschlag.api.club.ClubSettings
import com.kazox.aufschlag.repositories.ClubRow

internal fun ClubRow.decodeSettings(): ClubSettings = AppJson.decodeFromString(ClubSettings.serializer(), settingsJson)
