package no.elhub.auth.v1.common

import kotlin.time.Instant

data class GrantValidity(
    val validFrom: Instant,
    val validTo: Instant?,
)

/**
 * Starts no earlier than the start of today in Oslo, with no end unless requested.
 * The exclusive requested end must be strictly after now and the effective start.
 */
fun frameworkAgreementGrantValidity(
    requestedValidFrom: Instant?,
    requestedValidTo: Instant?,
    now: Instant,
    invalidValidity: () -> Nothing,
): GrantValidity {
    val startOfToday = now.startOfDayOslo()
    val validFrom = maxOf(requestedValidFrom ?: startOfToday, startOfToday)
    if (requestedValidTo != null && (now >= requestedValidTo || validFrom >= requestedValidTo)) {
        invalidValidity()
    }
    return GrantValidity(validFrom = validFrom, validTo = requestedValidTo)
}
