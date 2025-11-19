package no.elhub.auth.features.requests.create

import arrow.core.Either
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.ChangeOfSupplierRequestCommand
import no.elhub.auth.features.requests.create.command.RequestValidationError
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class CreateRequestAttributes(
    val requestType: AuthorizationRequest.Type
) : JsonApiAttributes

@Serializable
data class CreateRequestMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

@Serializable
data class CreateRequestRelationships(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
) : JsonApiRelationships

typealias CreateRequest = JsonApiRequest.SingleDocumentWithMeta<CreateRequestAttributes, CreateRequestMeta>

fun CreateRequestMeta.toChangeOfSupplierRequestCommand(): Either<RequestValidationError, ChangeOfSupplierRequestCommand> = ChangeOfSupplierRequestCommand(
    requestedBy = this.requestedBy,
    requestedFrom = this.requestedFrom,
    requestedFromName = this.requestedFromName,
    requestedForMeteringPointId = this.requestedForMeteringPointId,
    requestedForMeteringPointAddress = this.requestedForMeteringPointAddress,
    balanceSupplierContractName = this.balanceSupplierContractName
)
