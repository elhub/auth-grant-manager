package no.elhub.auth.features.requests.create.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
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
) : JsonApiResourceMeta

typealias JsonApiCreateRequest = JsonApiRequest.SingleDocumentWithMeta<CreateRequestAttributes, CreateRequestMeta>

fun JsonApiCreateRequest.toModel(): CreateRequestModel =
    CreateRequestModel(
        requestType = this.data.attributes.requestType,
        meta = this.data.meta.toModel()
    )

fun CreateRequestMeta.toModel(): no.elhub.auth.features.requests.create.model.CreateRequestMeta =
    no.elhub.auth.features.requests.create.model.CreateRequestMeta(
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedFromName = this.requestedFromName,
        requestedTo = this.requestedTo,
        requestedForMeteringPointId = this.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.requestedForMeteringPointAddress,
        balanceSupplierName = this.balanceSupplierName,
        balanceSupplierContractName = this.balanceSupplierContractName,
    )
