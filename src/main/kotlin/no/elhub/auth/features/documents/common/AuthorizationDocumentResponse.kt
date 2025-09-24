package no.elhub.auth.features.documents.common

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class DocumentResponseAttributes(
    val status: String,
    val createdAt: String,
    val updatedAt: String
) : JsonApiAttributes

@Serializable
data class DocumentRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne
) : JsonApiRelationships

typealias AuthorizationDocumentResponse = JsonApiResponse.SingleDocumentWithRelationships<DocumentResponseAttributes, DocumentRelationships>

fun AuthorizationDocument.toResponse() =
    AuthorizationDocumentResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationDocument",
            id = this.id.toString(),
            attributes = DocumentResponseAttributes(
                status = this.status.toString(),
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString()
            ),
            relationships = DocumentRelationships(
                requestedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.requestedBy,
                        type = "User"
                    )
                ),
                requestedFrom = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.requestedTo,
                        type = "User"
                    )
                )
            )
        )
    )
