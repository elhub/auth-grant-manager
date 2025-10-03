package no.elhub.auth.features.documents.common

import kotlinx.serialization.Serializable
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships
import java.net.URI

@Serializable
data class DocumentResponseAttributes(
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val reference: String
) : JsonApiAttributes

@Serializable
data class DocumentRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne
) : JsonApiRelationships

typealias AuthorizationDocumentResponse = JsonApiResponse.SingleDocumentWithRelationships<DocumentResponseAttributes, DocumentRelationships>

fun Pair<AuthorizationDocument, URI>.toResponse() = AuthorizationDocumentResponse(
    data =
    JsonApiResponseResourceObjectWithRelationships<DocumentResponseAttributes, DocumentRelationships>(
        id = this.first.id.toString(),
        type = "AuthorizationDocument",
        attributes = DocumentResponseAttributes(
            status = this.first.status.toString(),
            createdAt = this.first.createdAt.toString(),
            updatedAt = this.first.updatedAt.toString(),
            reference = this.second.toString()
        ),
        relationships = DocumentRelationships(
            requestedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.first.requestedBy,
                    type = "User"
                )
            ),
            requestedFrom = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.first.requestedFrom,
                    type = "User"

                )
            )
        )

    )
)
