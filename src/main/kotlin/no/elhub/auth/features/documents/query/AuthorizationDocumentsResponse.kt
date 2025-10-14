package no.elhub.auth.features.documents.query

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRelationships
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class DocumentResponseAttributes(
    val status: String,
    val createdAt: String,
    val updatedAt: String,
) : JsonApiAttributes

typealias AuthorizationDocumentResponse = JsonApiResponse.SingleDocumentWithRelationships<DocumentResponseAttributes, DocumentRelationships>
typealias AuthorizationDocumentsResponse = List<AuthorizationDocumentResponse>

fun List<AuthorizationDocument>.toResponse() = this.map {
    AuthorizationDocumentResponse(
        data =
        JsonApiResponseResourceObjectWithRelationships<DocumentResponseAttributes, DocumentRelationships>(
            id = it.id.toString(),
            type = "AuthorizationDocument",
            attributes = DocumentResponseAttributes(
                status = it.status.toString(),
                createdAt = it.createdAt.toString(),
                updatedAt = it.updatedAt.toString(),
            ),
            relationships = DocumentRelationships(
                requestedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = it.requestedBy,
                        type = "User"
                    )
                ),
                requestedFrom = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = it.requestedFrom,
                        type = "User"
                    )
                )
            )
        )
    )
}
