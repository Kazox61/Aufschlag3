package com.kazox.aufschlag.pricing

/**
 * Half-open slot start times (minutes since midnight, club-local) for a day's opening window —
 * the grid both the day-view and booking creation validate a chosen start against. `openMinute`/
 * `closeMinute` come from [com.kazox.aufschlag.api.club.DayOpeningHours]; a slot only counts if
 * it fully fits before closing.
 */
fun slotStartMinutes(openMinute: Int, closeMinute: Int, slotMinutes: Int): List<Int> =
    generateSequence(openMinute) { it + slotMinutes }
        .takeWhile { it + slotMinutes <= closeMinute }
        .toList()
