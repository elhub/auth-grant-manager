package no.elhub.auth.features.requests.create.model

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest

data class CreateRequestModel(
    val validTo: LocalDate,
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
