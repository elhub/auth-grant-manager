package no.elhub.auth.features.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TIME_ZONE = "Europe/Oslo"
private val ZONE_ID = ZoneId.of(TIME_ZONE)
private val ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun OffsetDateTime.toTimeZoneOffsetString(): String =
    this.toInstant()
        .atZone(ZONE_ID)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(ISO_OFFSET_FORMATTER)

fun currentTimeLocal(): OffsetDateTime = OffsetDateTime.now(ZONE_ID)

fun currentTimeUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

fun LocalDate.toTimeZoneOffsetDateTimeAtStartOfDay(): OffsetDateTime =
    this.toJavaLocalDate()
        .atStartOfDay(ZONE_ID)
        .toOffsetDateTime()

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.of(TIME_ZONE)).date
