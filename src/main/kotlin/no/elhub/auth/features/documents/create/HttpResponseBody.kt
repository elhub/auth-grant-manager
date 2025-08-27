package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class DocumentResponseAttributes(
    val status: String,
    val createdAt: String,
    val updatedAt: String
) : JsonApiAttributes

typealias HttpResponseBody = JsonApiResponse.SingleDocumentWithRelationships<DocumentResponseAttributes, DocumentRelationships>

fun AuthorizationDocument.toResponseBody(): HttpResponseBody {
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

    return HttpResponseBody(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationDocument",
            id = this.id.toString(),
            attributes = attributes,
            relationships = relationships
        )
    )
}
