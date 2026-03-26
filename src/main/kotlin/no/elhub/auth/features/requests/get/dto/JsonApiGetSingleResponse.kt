package no.elhub.auth.features.requests.get.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.common.dto.AuthorizationRequestResponseAttributes
import no.elhub.auth.features.requests.common.dto.AuthorizationRequestResponseLinks
import no.elhub.auth.features.requests.common.dto.AuthorizationRequestResponseRelationships
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

typealias GetRequestSingleResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    AuthorizationRequestResponseAttributes,
    AuthorizationRequestResponseRelationships,
    JsonApiResourceMetaMap,
    AuthorizationRequestResponseLinks
    >

fun AuthorizationRequest.toGetSingleResponse() =
    GetRequestSingleResponse(
        data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes = AuthorizationRequestResponseAttributes(
                status = this.status.name,
                requestType = this.type.name,
                validTo = this.validTo.toTimeZoneOffsetString(),
                updatedAt = this.updatedAt.toTimeZoneOffsetString(),
                createdAt = this.createdAt.toTimeZoneOffsetString(),
            ),
            relationships = AuthorizationRequestResponseRelationships(
                requestedBy = this.requestedBy.toJsonApiRelationship(),
                requestedFrom = this.requestedFrom.toJsonApiRelationship(),
                requestedTo = this.requestedTo.toJsonApiRelationship(),
                approvedBy = this.approvedBy?.toJsonApiRelationship(),
                authorizationGrant = this.grantId?.let { grantId ->
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
            meta = JsonApiResourceMetaMap(
                buildMap {
                    this@toGetSingleResponse.properties.forEach { prop ->
                        put(prop.key, prop.value)
                    }
                }
            ),
            links = AuthorizationRequestResponseLinks(
                self = "${REQUESTS_PATH}/${this.id}"
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink("${REQUESTS_PATH}/${this.id}"),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", this@toGetSingleResponse.createdAt.toTimeZoneOffsetString())
            }
        )
    )
