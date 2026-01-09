package no.elhub.auth.features.requests.create.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CreateRequestModel(
    val authorizedParty: AuthorizationParty,
    val requestType: AuthorizationRequest.Type,
    val meta: CreateRequestMeta,
)

data class CreateRequestMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedFromName: String,
    val requestedTo: PartyIdentifier,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
)

@OptIn(ExperimentalTime::class)
fun defaultRequestValidTo(): LocalDate {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    return now.plus(DatePeriod(days = 30))
}

@OptIn(ExperimentalTime::class)
fun today(): LocalDate {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    return now
}
