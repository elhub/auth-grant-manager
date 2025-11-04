package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class DocumentRequestAttributes(
    val documentType: AuthorizationDocument.Type
) : JsonApiAttributes

@Serializable
data class DocumentRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne
) : JsonApiRelationships

@Serializable
data class DocumentMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

typealias Request = JsonApiRequest.SingleDocumentWithRelationshipsAndMeta<DocumentRequestAttributes, DocumentRelationships, DocumentMeta>

fun Request.toCommand() = Command(
    type = this.data.attributes.documentType,
    requestedFrom = PartyRef(
        type = AuthorizationParty.ElhubResource.valueOf(this.data.relationships.requestedFrom.data.type),
        resourceId = this.data.relationships.requestedFrom.data.id
    ),
    requestedFromName = this.data.meta.requestedFromName,
    requestedBy = PartyRef(
        type = AuthorizationParty.ElhubResource.valueOf(this.data.relationships.requestedBy.data.type),
        resourceId = this.data.relationships.requestedBy.data.id
    ),
    balanceSupplierName = this.data.meta.balanceSupplierName,
    balanceSupplierContractName = this.data.meta.balanceSupplierContractName,
    meteringPointId = this.data.meta.requestedForMeteringPointId,
    meteringPointAddress = this.data.meta.requestedForMeteringPointAddress
)
