package no.elhub.auth.v0.features.requests.common

import kotlinx.datetime.LocalDate
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.requests.AuthorizationRequest

data class CreateRequestBusinessModel(
    val authorizedParty: AuthorizationParty,
    val requestType: AuthorizationRequest.Type,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val meta: CreateRequestBusinessMeta,
)

data class CreateRequestBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: LocalDate? = null,
    val redirectURI: String? = null,
)
