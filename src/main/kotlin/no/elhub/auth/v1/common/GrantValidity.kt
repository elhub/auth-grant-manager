package no.elhub.auth.v1.common

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.time.Instant

private const val CONSUMABLE_GRANT_VALID_YEARS = 2

data class GrantValidity(
    val validFrom: Instant,
    val validTo: Instant?,
)

/**
 * Move-in and supplier-change grants start at the approval or confirmation instant.
 * The exclusive end is midnight after the calendar date two years ahead.
 */
fun consumableGrantValidity(now: Instant): GrantValidity {
    val today = now.toOsloDate()
    return GrantValidity(
        validFrom = now,
        validTo = today.plus(CONSUMABLE_GRANT_VALID_YEARS, DateTimeUnit.YEAR)
            .plus(1, DateTimeUnit.DAY).startOfDayOslo(),
    )
}

/**
 * Starts no earlier than approval or confirmation, with no end unless requested.
 * The exclusive requested end must be strictly after now and the effective start.
 */
fun frameworkAgreementGrantValidity(
    requestedValidFrom: Instant?,
    requestedValidTo: Instant?,
    now: Instant,
    invalidValidity: () -> Nothing,
): GrantValidity {
    val validFrom = maxOf(requestedValidFrom ?: now, now)
    if (requestedValidTo != null && (now >= requestedValidTo || validFrom >= requestedValidTo)) {
        invalidValidity()
    }
    return GrantValidity(validFrom = validFrom, validTo = requestedValidTo)
}
