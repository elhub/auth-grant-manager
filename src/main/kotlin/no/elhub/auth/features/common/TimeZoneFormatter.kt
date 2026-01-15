package no.elhub.auth.features.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TIME_ZONE = ZoneId.of("Europe/Oslo")
val ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun OffsetDateTime.toTimeZoneOffsetString(): String =
    this.toInstant()
        .atZone(TIME_ZONE)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(ISO_OFFSET_FORMATTER)

fun currentTimeWithTimeZone(): OffsetDateTime = OffsetDateTime.now(TIME_ZONE)

fun LocalDate.toTimeZoneOffsetDateTimeAtStartOfDay(): OffsetDateTime =
    this.toJavaLocalDate()
        .atStartOfDay(TIME_ZONE)
        .toOffsetDateTime()
