package no.elhub.auth.v1.common

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class GrantValidity(
    val validFrom: Instant,
    val validTo: Instant?,
)

/**
 * Starts no earlier than the start of today in Oslo, with no end unless requested.
 * The requested end must be after now and not before the effective start.
 */
fun frameworkAgreementGrantValidity(
    requestedValidFrom: Instant?,
    requestedValidTo: Instant?,
    now: Instant,
    invalidValidity: () -> Nothing,
): GrantValidity {
    val oslo = TimeZone.of("Europe/Oslo")
    val startOfToday = now.toLocalDateTime(oslo).date.atStartOfDayIn(oslo)
    val validFrom = maxOf(requestedValidFrom ?: startOfToday, startOfToday)
    if (requestedValidTo != null && (requestedValidTo <= now || validFrom > requestedValidTo)) {
        invalidValidity()
    }
    return GrantValidity(validFrom = validFrom, validTo = requestedValidTo)
}
