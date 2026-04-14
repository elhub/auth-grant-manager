package no.elhub.auth.features.requests.common

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.requests.AuthorizationRequest

data class CreateRequestBusinessModel(
    val authorizedParty: AuthorizationParty,
    val requestType: AuthorizationRequest.Type,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
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
