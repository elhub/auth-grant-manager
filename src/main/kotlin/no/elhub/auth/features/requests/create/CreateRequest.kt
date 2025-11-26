package no.elhub.auth.features.requests.create

import arrow.core.Either
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.ChangeOfSupplierRequestCommand
import no.elhub.auth.features.requests.create.command.RequestValidationError
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class CreateRequestAttributes(
    val requestType: AuthorizationRequest.Type,
    val validTo: String
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
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

typealias CreateRequest = JsonApiRequest.SingleDocumentWithMeta<CreateRequestAttributes, CreateRequestMeta>

fun CreateRequest.toChangeOfSupplierRequestCommand(): Either<RequestValidationError, ChangeOfSupplierRequestCommand> = ChangeOfSupplierRequestCommand(
    requestedBy = this.data.meta.requestedBy,
    requestedFrom = this.data.meta.requestedFrom,
    requestedFromName = this.data.meta.requestedFromName,
    requestedTo = this.data.meta.requestedTo,
    validTo = this.data.attributes.validTo,
    requestedForMeteringPointId = this.data.meta.requestedForMeteringPointId,
    requestedForMeteringPointAddress = this.data.meta.requestedForMeteringPointAddress,
    balanceSupplierName = this.data.meta.balanceSupplierName,
    balanceSupplierContractName = this.data.meta.balanceSupplierContractName
)
