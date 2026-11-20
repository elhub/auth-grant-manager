package no.elhub.auth.v1.acceptance.support

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

private val oslo = ZoneId.of("Europe/Oslo")

/** Midnight in Oslo, [daysFromNow] days from today, formatted like the API (e.g. `2026-12-01T00:00:00+01:00`). */
fun osloStartOfDay(daysFromNow: Long): String =
    LocalDate.now(oslo).plusDays(daysFromNow).atStartOfDay(oslo).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun instant(isoDateTime: String): Instant = Instant.parse(isoDateTime)

fun ApiResponse.instant(path: String): Instant =
    Instant.parse(checkNotNull(field(path)) { "Response has no '$path'" })
