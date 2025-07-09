package no.elhub.auth.grantmanager.presentation.features.documents

import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.presentation.model.AuthorizationDocument
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.request.JsonApiRequest
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class DocumentRequestAttributes(
    val meteringPoint: String
) : JsonApiAttributes

@Serializable
data class DocumentResponseAttributes(
    val status: String,
    val createdAt: String,
    val updatedAt: String
) : JsonApiAttributes

@Serializable
data class DocumentRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne
) : JsonApiRelationships

typealias PostAuthorizationDocumentRequest = JsonApiRequest.SingleDocumentWithRelationships<DocumentRequestAttributes, DocumentRelationships>
typealias PostAuthorizationDocumentResponse = JsonApiResponse.SingleDocumentWithRelationships<DocumentResponseAttributes, DocumentRelationships>

fun AuthorizationDocument.toPostAuthorizationDocumentResponse(): PostAuthorizationDocumentResponse {
    val attributes = DocumentResponseAttributes(
        status = this.status.toString(),
        createdAt = this.createdAt.toString(),
        updatedAt = this.updatedAt.toString()
    )

    val relationships = DocumentRelationships(
        requestedBy = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.requestedBy,
                type = "User"
            )
        ),
        requestedTo = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.requestedTo,
                type = "User"
            )
        )
    )

    return PostAuthorizationDocumentResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationDocument",
            id = this.id.toString(),
            attributes = attributes,
            relationships = relationships
        )
    )
}
