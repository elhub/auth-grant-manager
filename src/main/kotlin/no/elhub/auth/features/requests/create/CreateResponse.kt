package no.elhub.auth.features.requests.create

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
data class CreateRequestResponseAttributes(
    val status: String,
    val requestType: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class CreateRequestResponseRelationShips(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
) : JsonApiRelationships

@Serializable
data class CreateRequestResponseMeta(
    val createdAt: String,
    val updatedAt: String,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

@Serializable
data class CreateRequestResponseLinks(
    val self: String
) : JsonApiResourceLinks

typealias CreateRequestResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    CreateRequestResponseAttributes,
    CreateRequestResponseRelationShips,
    CreateRequestResponseMeta,
    CreateRequestResponseLinks
    >

fun AuthorizationRequest.toCreateResponse() = CreateRequestResponse(
    data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
        type = "AuthorizationRequest",
        id = this.id.toString(),
        attributes = CreateRequestResponseAttributes(
            status = this.status.name,
            requestType = this.type.name,
            validTo = this.validTo.toString()
        ),
        relationships = CreateRequestResponseRelationShips(
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
        ),
        meta = CreateRequestResponseMeta(
            createdAt = this.createdAt.toString(),
            updatedAt = this.updatedAt.toString(),
            requestedFromName = this.properties["requestedFromName"].toString(),
            requestedForMeteringPointId = this.properties["requestedForMeteringPointId"].toString(),
            requestedForMeteringPointAddress = this.properties["requestedForMeteringPointAddress"].toString(),
            balanceSupplierName = this.properties["balanceSupplierName"].toString(),
            balanceSupplierContractName = this.properties["balanceSupplierContractName"].toString(),
        ),
        links = CreateRequestResponseLinks(
            self = "https://api.elhub.no/authorization-requests/${this.id}"
        )
    ),
    links = JsonApiLinks.ResourceObjectLink("https://api.elhub.no/authorization-requests"),
    meta = JsonApiMeta(
        buildJsonObject {
            put("createdAt", LocalDateTime.now().toString())
        }
    )
)
