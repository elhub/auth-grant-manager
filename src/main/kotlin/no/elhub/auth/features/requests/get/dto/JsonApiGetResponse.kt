package no.elhub.auth.features.requests.get.dto

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
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks
import java.time.LocalDateTime

@Serializable
data class GetRequestResponseAttributes(
    val status: String,
    val requestType: String,
    val createdAt: String,
    val updatedAt: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class GetRequestResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null, // only present after a request is accepted
) : JsonApiRelationships

@Serializable
data class GetRequestResponseLinks(
    val self: String
) : JsonApiResourceLinks

@Serializable
data class GetRequestResponseMeta(
    val createdAt: String,
    val updatedAt: String,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

typealias GetRequestResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    GetRequestResponseAttributes,
    GetRequestResponseRelationships,
    GetRequestResponseMeta,
    GetRequestResponseLinks
    >

fun AuthorizationRequest.toGetResponse() =
    GetRequestResponse(
        data =
        JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes = GetRequestResponseAttributes(
                status = this.status.name,
                requestType = this.type.name,
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString(),
                validTo = this.validTo.toString(),
            ),
            relationships = GetRequestResponseRelationships(
                requestedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.requestedBy.type.name,
                        id = this.requestedBy.resourceId
                    )
                ),
                requestedFrom = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.requestedFrom.type.name,
                        id = this.requestedFrom.resourceId
                    )
                ),
                requestedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.requestedTo.type.name,
                        id = this.requestedTo.resourceId
                    )
                ),
                // only present after a request is accepted
                approvedBy = if (this.status == AuthorizationRequest.Status.Accepted) {
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            type = this.requestedTo.type.name,
                            id = this.requestedTo.resourceId
                        )
                    )
                } else {
                    null
                }
            ),
            meta = GetRequestResponseMeta(
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString(),
                requestedFromName = this.properties["requestedFromName"].toString(),
                requestedForMeteringPointId = this.properties["requestedForMeteringPointId"].toString(),
                requestedForMeteringPointAddress = this.properties["requestedForMeteringPointAddress"].toString(),
                balanceSupplierName = this.properties["balanceSupplierName"].toString(),
                balanceSupplierContractName = this.properties["balanceSupplierContractName"].toString(),
            ),
            links =
            GetRequestResponseLinks(
                self = "https://api.elhub.no/authorization-requests/${this.id}"
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink("https://api.elhub.no/authorization-requests"),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", LocalDateTime.now().toString())
            }
        )
    )
