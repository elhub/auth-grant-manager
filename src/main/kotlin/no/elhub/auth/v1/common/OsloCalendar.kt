package no.elhub.auth.v1.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val OSLO = TimeZone.of("Europe/Oslo")

fun now(): Instant = Clock.System.now()

fun Instant.toOsloLocalDateTime(): LocalDateTime = toLocalDateTime(OSLO)

fun Instant.toOsloDate(): LocalDate = toOsloLocalDateTime().date

fun Instant.startOfDayOslo(): Instant = toOsloDate().startOfDayOslo()

fun LocalDate.startOfDayOslo(): Instant = atStartOfDayIn(OSLO)

fun String.toOsloDateOrNull(): LocalDate? =
    try {
        LocalDate.parse(this)
    } catch (_: IllegalArgumentException) {
        try {
            Instant.parse(this).toOsloDate()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
