package no.elhub.auth.features.documents.get

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.grants.GRANTS_PATH
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
    val requestedTo: JsonApiRelationshipToOne,
    val signedBy: JsonApiRelationshipToOne? = null,
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
                createdAt = this.createdAt.toTimeZoneOffsetString(),
                updatedAt = this.updatedAt.toTimeZoneOffsetString()
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
                requestedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.requestedTo.resourceId,
                        type = this.requestedTo.type.name
                    )
                ),
                signedBy = this.signedBy?.let {
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = it.resourceId,
                            type = it.type.name
                        )
                    )
                },
                grant = this.grantId?.let { grantId ->
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = grantId.toString(),
                            type = "AuthorizationGrant"
                        ),
                        links = JsonApiLinks.RelationShipLink(
                            self = "${GRANTS_PATH}/$grantId"
                        )
                    )
                }
            ),
            meta = buildJsonObject {
                put("createdAt", JsonPrimitive(this@toGetResponse.createdAt.toTimeZoneOffsetString()))
                put("updatedAt", JsonPrimitive(this@toGetResponse.updatedAt.toTimeZoneOffsetString()))
                this@toGetResponse.properties.forEach {
                    put(it.key, JsonPrimitive(it.value))
                }
            }
        ),
        links = JsonApiLinks.ResourceObjectLink("${DOCUMENTS_PATH}/${this.id}")
    )
