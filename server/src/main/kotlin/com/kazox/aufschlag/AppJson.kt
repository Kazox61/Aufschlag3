package com.kazox.aufschlag

import kotlinx.serialization.json.Json

/** The one [Json] configuration for the server — HTTP content negotiation and every jsonb
 *  column (club settings, price-rule days, application data) encode/decode through it, so a
 *  DTO round-trips identically whether it went over the wire or through the database. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
