package no.elhub.auth.features.documents.create

import arrow.core.Either
import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.ChangeOfSupplierDocumentCommand
import no.elhub.auth.features.documents.create.command.ValidationError
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta

@Serializable
data class DocumentRequestAttributes(
    val documentType: AuthorizationDocument.Type
) : JsonApiAttributes

@Serializable
data class DocumentMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val signedBy: PartyIdentifier,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

@Serializable
data class PartyIdentifier(
    val idType: PartyIdentifierType,
    val idValue: String
)

@Serializable
enum class PartyIdentifierType {
    NationalIdentityNumber,
    OrganizationNumber,
    GlobalLocationNumber
}

// We need to move this to json wrapper
@Serializable
data class RequestData(
    val type: String,
    val attributes: DocumentRequestAttributes,
    val meta: DocumentMeta,
)

@Serializable
data class Request(
    val data: RequestData,
)

fun DocumentMeta.toChangeOfSupplierDocumentCommand(): Either<ValidationError, ChangeOfSupplierDocumentCommand> = ChangeOfSupplierDocumentCommand(
    requestedBy = this.requestedBy,
    requestedFrom = this.requestedFrom,
    requestedTo = this.requestedTo,
    signedBy = this.signedBy,
    requestedFromName = this.requestedFromName,
    balanceSupplierName = this.balanceSupplierName,
    balanceSupplierContractName = this.balanceSupplierContractName,
    meteringPointId = this.requestedForMeteringPointId,
    meteringPointAddress = this.requestedForMeteringPointAddress
)
