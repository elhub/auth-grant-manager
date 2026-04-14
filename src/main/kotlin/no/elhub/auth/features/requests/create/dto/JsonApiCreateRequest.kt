package no.elhub.auth.features.requests.create.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.CreateRequestBusinessMeta
import no.elhub.auth.features.requests.create.model.CreateRequestCoreMeta
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class CreateRequestAttributes(
    val requestType: AuthorizationRequest.Type,
) : JsonApiAttributes

@Serializable
data class CreateRequestMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedFromName: String,
    val requestedTo: PartyIdentifier,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: LocalDate? = null,
    val redirectURI: String? = null,
) : JsonApiResourceMeta

typealias JsonApiCreateRequest = JsonApiRequest.SingleDocumentWithMeta<CreateRequestAttributes, CreateRequestMeta>

fun JsonApiCreateRequest.toModel(authorizedParty: AuthorizationParty): CreateRequestModel =
    CreateRequestModel(
        authorizedParty = authorizedParty,
        requestType = this.data.attributes.requestType,
        coreMeta = CreateRequestCoreMeta(
            requestedBy = this.data.meta.requestedBy,
            requestedFrom = this.data.meta.requestedFrom,
            requestedTo = this.data.meta.requestedTo,
        ),
        businessMeta = CreateRequestBusinessMeta(
            requestedFromName = this.data.meta.requestedFromName,
            requestedForMeteringPointId = this.data.meta.requestedForMeteringPointId,
            requestedForMeteringPointAddress = this.data.meta.requestedForMeteringPointAddress,
            balanceSupplierName = this.data.meta.balanceSupplierName,
            balanceSupplierContractName = this.data.meta.balanceSupplierContractName,
            moveInDate = this.data.meta.moveInDate,
            redirectURI = this.data.meta.redirectURI,
        ),
    )
