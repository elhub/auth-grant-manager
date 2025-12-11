package no.elhub.auth.features.requests.query.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks
import java.time.LocalDateTime

@Serializable
data class GetRequestCollectionResponseAttributes(
    val status: String,
    val requestType: String,
    val createdAt: String,
    val updatedAt: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class GetRequestCollectionResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null, // only present after a request is accepted
) : JsonApiRelationships

@Serializable
data class GetRequestCollectionResponseLinks(
    val self: String
) : JsonApiResourceLinks

@Serializable
data class GetRequestCollectionResponseMeta(
    val createdAt: String,
    val updatedAt: String,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

typealias GetRequestCollectionResponse = JsonApiResponse.CollectionDocumentWithRelationshipsAndMetaAndLinks<
    GetRequestCollectionResponseAttributes,
    GetRequestCollectionResponseRelationships,
    GetRequestCollectionResponseMeta,
    GetRequestCollectionResponseLinks
    >

fun List<AuthorizationRequest>.toGetCollectionResponse() =
    GetRequestCollectionResponse(
        data = this.map {
            JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
                id = it.id.toString(),
                type = "AuthorizationRequest",
                attributes = GetRequestCollectionResponseAttributes(
                    status = it.status.toString(),
                    requestType = it.type.toString(),
                    createdAt = it.createdAt.toString(),
                    updatedAt = it.updatedAt.toString(),
                    validTo = it.validTo.toString()
                ),
                relationships = GetRequestCollectionResponseRelationships(
                    requestedBy = it.requestedBy.toJsonApiRelationship(),
                    requestedFrom = it.requestedFrom.toJsonApiRelationship(),
                    requestedTo = it.requestedTo.toJsonApiRelationship(),
                    approvedBy = it.approvedBy?.toJsonApiRelationship()
                ),
                meta = GetRequestCollectionResponseMeta(
                    createdAt = it.createdAt.toString(),
                    updatedAt = it.updatedAt.toString(),
                    requestedFromName = it.properties["requestedFromName"].toString(),
                    requestedForMeteringPointId = it.properties["requestedForMeteringPointId"].toString(),
                    requestedForMeteringPointAddress = it.properties["requestedForMeteringPointAddress"].toString(),
                    balanceSupplierName = it.properties["balanceSupplierName"].toString(),
                    balanceSupplierContractName = it.properties["balanceSupplierContractName"].toString(),
                ),
                links = GetRequestCollectionResponseLinks(
                    self = "${REQUESTS_PATH}/${it.id}",
                )
            )
        },
        links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", LocalDateTime.now().toString())
            }
        )
    )
