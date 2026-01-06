package no.elhub.auth.features.common

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_ZONE = ZoneId.of("Europe/Oslo")
private val ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun OffsetDateTime.toTimeZoneOffsetString(): String =
    this.toInstant()
        .atZone(TIME_ZONE)
        .format(ISO_OFFSET_FORMATTER)

fun currentTimeWithTimeZone(): OffsetDateTime = OffsetDateTime.now(TIME_ZONE)
