package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
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

fun Request.toCommand() = Command(
    type = this.data.attributes.documentType,
    requestedByIdentifier = this.data.meta.requestedBy,
    requestedFromIdentifier = this.data.meta.requestedFrom,
    requestedToIdentifier = this.data.meta.requestedTo,
    signedByIdentifier = this.data.meta.signedBy,
    requestedFromName = this.data.meta.requestedFromName,
    balanceSupplierName = this.data.meta.balanceSupplierName,
    balanceSupplierContractName = this.data.meta.balanceSupplierContractName,
    meteringPointId = this.data.meta.requestedForMeteringPointId,
    meteringPointAddress = this.data.meta.requestedForMeteringPointAddress
)
