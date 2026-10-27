package no.elhub.auth.v1.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val OSLO = TimeZone.of("Europe/Oslo")

fun now() = Clock.System.now()

fun nowOslo(instant: Instant = now()) = instant.toLocalDateTime(OSLO)

fun todayOslo(instant: Instant = now()) = nowOslo(instant).date

fun startOfTodayOslo(instant: Instant = now()) = todayOslo(instant).startOfDayOslo()

fun LocalDate.startOfDayOslo() = atStartOfDayIn(OSLO)

fun LocalDate.endOfDayOslo() = atTime(23, 59, 59).toInstant(OSLO)
