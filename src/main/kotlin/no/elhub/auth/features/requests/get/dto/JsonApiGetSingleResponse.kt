package no.elhub.auth.features.requests.get.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
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
data class GetRequestSingleResponseAttributes(
    val status: String,
    val requestType: String,
    val createdAt: String,
    val updatedAt: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class GetRequestSingleResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null,
    val grant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships

@Serializable
data class GetRequestSingleResponseLinks(
    val self: String
) : JsonApiResourceLinks

@Serializable
data class GetRequestSingleResponseMeta(
    val createdAt: String,
    val updatedAt: String,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

typealias GetRequestSingleResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    GetRequestSingleResponseAttributes,
    GetRequestSingleResponseRelationships,
    GetRequestSingleResponseMeta,
    GetRequestSingleResponseLinks
    >

fun AuthorizationRequest.toGetSingleResponse() =
    GetRequestSingleResponse(
        data =
        JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes = GetRequestSingleResponseAttributes(
                status = this.status.name,
                requestType = this.type.name,
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString(),
                validTo = this.validTo.toString(),
            ),
            relationships = GetRequestSingleResponseRelationships(
                requestedBy = this.requestedBy.toJsonApiRelationship(),
                requestedFrom = this.requestedFrom.toJsonApiRelationship(),
                requestedTo = this.requestedTo.toJsonApiRelationship(),
                approvedBy = this.approvedBy?.toJsonApiRelationship(),
                grant = this.grantId?.let { grantId ->
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = grantId.toString(),
                            type = "AuthorizationGrant"
                        ),
                        links = JsonApiLinks.RelationShipLink(
                            self = "$GRANTS_PATH/$grantId"
                        )
                    )
                }
            ),
            meta = GetRequestSingleResponseMeta(
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString(),
                requestedFromName = this.properties["requestedFromName"].toString(),
                requestedForMeteringPointId = this.properties["requestedForMeteringPointId"].toString(),
                requestedForMeteringPointAddress = this.properties["requestedForMeteringPointAddress"].toString(),
                balanceSupplierName = this.properties["balanceSupplierName"].toString(),
                balanceSupplierContractName = this.properties["balanceSupplierContractName"].toString(),
            ),
            links =
            GetRequestSingleResponseLinks(
                self = "${REQUESTS_PATH}/${this.id}"
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", LocalDateTime.now().toString())
            }
        )
    )
