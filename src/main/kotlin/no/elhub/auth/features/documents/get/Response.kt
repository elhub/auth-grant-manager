package no.elhub.auth.features.documents.get

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class GetDocumentResponseAttributes(
    val status: String,
    val createdAt: String,
    val updatedAt: String
) : JsonApiAttributes

@Serializable
data class GetDocumentResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val grant: JsonApiRelationshipToOne? = null
) : JsonApiRelationships

typealias GetDocumentResponse = JsonApiResponse.SingleDocumentWithRelationships<GetDocumentResponseAttributes, GetDocumentResponseRelationships>

fun AuthorizationDocument.toGetResponse() =
    GetDocumentResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationDocument",
            id = this.id.toString(),
            attributes = GetDocumentResponseAttributes(
                status = this.status.toString(),
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString()
            ),
            relationships = GetDocumentResponseRelationships(
                requestedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.requestedBy.resourceId,
                        type = this.requestedBy.type.name
                    )
                ),
                requestedFrom = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.requestedFrom.resourceId,
                        type = this.requestedFrom.type.name
                    )
                ),
                grant = this.grantId?.let { grantId ->
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = grantId,
                            type = "AuthorizationGrant"
                        ),
                        links = JsonApiLinks.RelationShipLink(
                            self = "authorization-grants/$grantId"
                        )
                    )
                }
            ),
            meta = buildJsonObject {
                put("createdAt", JsonPrimitive(this@toGetResponse.createdAt.toString()))
                put("updatedAt", JsonPrimitive(this@toGetResponse.updatedAt.toString()))
                this@toGetResponse.properties.forEach {
                    put(it.key, JsonPrimitive(it.value))
                }
            }
        ),
        links = JsonApiLinks.ResourceObjectLink("/authorization-documents/${this.id}")
    )
