package no.elhub.auth.features.requests.update.dto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.common.requireProperty
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
data class UpdateRequestResponseAttributes(
    val status: String,
    val requestType: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class UpdateRequestResponseRelationShips(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null,
    val grant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships

@Serializable
data class UpdateRequestResponseMeta(
    val createdAt: String,
    val updatedAt: String,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : JsonApiResourceMeta

@Serializable
data class UpdateRequestResponseLinks(
    val self: String,
) : JsonApiResourceLinks

typealias UpdateRequestResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    UpdateRequestResponseAttributes,
    UpdateRequestResponseRelationShips,
    UpdateRequestResponseMeta,
    UpdateRequestResponseLinks
    >

fun AuthorizationRequest.toUpdateResponse() =
    UpdateRequestResponse(
        data =
        JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes =
            UpdateRequestResponseAttributes(
                status = this.status.name,
                requestType = this.type.name,
                validTo = this.validTo.toString()
            ),
            relationships = UpdateRequestResponseRelationShips(
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
                approvedBy = this.approvedBy?.let {
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            type = it.type.name,
                            id = it.resourceId
                        )
                    )
                },
                grant = this.grantId?.let {
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = it.toString(),
                            type = "AuthorizationGrant"
                        ),
                        links = JsonApiLinks.RelationShipLink(
                            self = "$GRANTS_PATH/$it"
                        )
                    )
                },
            ),
            meta = UpdateRequestResponseMeta(
                createdAt = this.createdAt.toString(),
                updatedAt = this.updatedAt.toString(),
                requestedFromName = this.properties.requireProperty("requestedFromName"),
                requestedForMeteringPointId = this.properties.requireProperty("requestedForMeteringPointId"),
                requestedForMeteringPointAddress = this.properties.requireProperty("requestedForMeteringPointAddress"),
                balanceSupplierName = this.properties.requireProperty("balanceSupplierName"),
                balanceSupplierContractName = this.properties.requireProperty("balanceSupplierContractName"),
            ),
            links =
            UpdateRequestResponseLinks(
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
