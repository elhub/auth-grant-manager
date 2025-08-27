package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class DocumentRequestAttributes(
    val meteringPoint: String
) : JsonApiAttributes

@Serializable
data class DocumentRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne
) : JsonApiRelationships

typealias HttpRequestBody = JsonApiRequest.SingleDocumentWithRelationships<DocumentRequestAttributes, DocumentRelationships>

fun HttpRequestBody.toCreateDocumentCommand() = CreateDocumentCommand(
    type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
    requestedBy = this.data.relationships.requestedBy.data.id,
    requestedTo = this.data.relationships.requestedTo.data.id,
    meteringPoint = this.data.attributes.meteringPoint,
)
