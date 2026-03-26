package no.elhub.auth.features.requests.query.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeLocal
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

typealias GetRequestCollectionResponse = JsonApiResponse.CollectionDocumentWithRelationshipsAndMetaAndLinks<
    AuthorizationRequestResponseAttributes,
    AuthorizationRequestResponseRelationships,
    JsonApiResourceMetaMap,
    AuthorizationRequestResponseLinks
    >

fun List<AuthorizationRequest>.toGetCollectionResponse() =
    GetRequestCollectionResponse(
        data = this.map { request ->
            JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
                id = request.id.toString(),
                type = "AuthorizationRequest",
                attributes = AuthorizationRequestResponseAttributes(
                    status = request.status.name,
                    requestType = request.type.name,
                    validTo = request.validTo.toTimeZoneOffsetString(),
                    createdAt = request.createdAt.toTimeZoneOffsetString(),
                    updatedAt = request.updatedAt.toTimeZoneOffsetString(),
                ),
                relationships = AuthorizationRequestResponseRelationships(
                    requestedBy = request.requestedBy.toJsonApiRelationship(),
                    requestedFrom = request.requestedFrom.toJsonApiRelationship(),
                    requestedTo = request.requestedTo.toJsonApiRelationship(),
                    approvedBy = request.approvedBy?.toJsonApiRelationship(),
                    authorizationGrant = request.grantId?.let { grantId ->
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
                        request.properties.forEach { prop ->
                            put(prop.key, prop.value)
                        }
                    }
                ),
                links = AuthorizationRequestResponseLinks(
                    self = "${REQUESTS_PATH}/${request.id}",
                )
            )
        },
        links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeLocal().toTimeZoneOffsetString())
            }
        )
    )
