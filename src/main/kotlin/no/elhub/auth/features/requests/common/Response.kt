package no.elhub.auth.features.requests.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships
import java.time.LocalDateTime

@Serializable
data class RequestResponseAttribute(
    val status: String,
    val requestType: String,
    val createdAt: String,
    val updatedAt: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class ResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
) : JsonApiRelationships

typealias AuthorizationRequestResponse = JsonApiResponse.SingleDocumentWithRelationships<RequestResponseAttribute, ResponseRelationships>
typealias AuthorizationRequestListResponse = JsonApiResponse.CollectionDocumentWithRelationships<RequestResponseAttribute, ResponseRelationships>

fun AuthorizationRequest.toResponse() = AuthorizationRequestResponse(
    data = JsonApiResponseResourceObjectWithRelationships(
        id = this.id.toString(),
        type = "AuthorizationRequest",
        attributes = RequestResponseAttribute(
            status = this.status.toString(),
            requestType = this.type.toString(),
            createdAt = this.createdAt.toString(),
            updatedAt = this.updatedAt.toString(),
            validTo = this.validTo.toString()
        ),
        relationships = ResponseRelationships(
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
            )
        ),
        links = JsonApiLinks.ResourceObjectLink(
            self = "/authorization-requests/${this.id}",
        )
    ),
    links = JsonApiLinks.ResourceObjectLink(
        self = "/authorization-requests",
    ),
    meta = JsonApiMeta(
        buildJsonObject {
            put("createdAt", LocalDateTime.now().toString())
        }
    )
)

fun List<AuthorizationRequest>.toResponse() = AuthorizationRequestListResponse(
    data = this.map { it.toResponse().data },
    links = JsonApiLinks.ResourceObjectLink("/authorization-requests")
)
