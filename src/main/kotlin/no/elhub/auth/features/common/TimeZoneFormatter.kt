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

private val TIME_ZONE = ZoneId.of("Europe/Oslo")
private val ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun OffsetDateTime.toTimeZoneOffsetString(): String =
    this.toInstant()
        .atZone(TIME_ZONE)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(ISO_OFFSET_FORMATTER)

fun currentTimeLocal(): OffsetDateTime = OffsetDateTime.now(TIME_ZONE)

fun currentTimeUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

fun LocalDate.toTimeZoneOffsetDateTimeAtStartOfDay(): OffsetDateTime =
    this.toJavaLocalDate()
        .atStartOfDay(TIME_ZONE)
        .toOffsetDateTime()

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

