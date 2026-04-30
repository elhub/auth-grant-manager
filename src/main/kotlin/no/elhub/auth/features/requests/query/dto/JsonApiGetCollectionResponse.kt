package no.elhub.auth.features.requests.query.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.currentTimeOslo
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.common.toPaginationLinks
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.common.dto.AuthorizationRequestResponseAttributes
import no.elhub.auth.features.requests.common.dto.AuthorizationRequestResponseLinks
import no.elhub.auth.features.requests.common.dto.AuthorizationRequestResponseRelationships
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

typealias GetRequestCollectionResponse = PaginatedCollectionResponse<
        JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks<
                AuthorizationRequestResponseAttributes,
                AuthorizationRequestResponseRelationships,
                JsonApiResourceMetaMap,
                AuthorizationRequestResponseLinks
                >
        >

fun Page<AuthorizationRequest>.toGetCollectionResponse(): GetRequestCollectionResponse {
    val p = this.pagination

    return GetRequestCollectionResponse(
        data = this.items.map { request ->
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
                    self = "$REQUESTS_PATH/${request.id}",
                )
            )
        },
        // TODO fix status param here (and in documents/query)
        links = toPaginationLinks(REQUESTS_PATH),
        meta = buildJsonObject {
            put("createdAt", currentTimeOslo().toTimeZoneOffsetString())
            put("totalItems", this@toGetCollectionResponse.totalItems)
            put("totalPages", this@toGetCollectionResponse.totalPages)
            put("page", p.page)
            put("pageSize", p.size)
        }
    )
}
