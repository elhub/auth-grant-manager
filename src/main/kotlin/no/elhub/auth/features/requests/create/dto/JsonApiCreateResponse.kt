package no.elhub.auth.features.requests.create.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.toTimeZoneOffsetString
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

typealias CreateRequestResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    AuthorizationRequestResponseAttributes,
    AuthorizationRequestResponseRelationships,
    JsonApiResourceMetaMap,
    AuthorizationRequestResponseLinks
    >

fun AuthorizationRequest.toCreateResponse() =
    CreateRequestResponse(
        data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes = AuthorizationRequestResponseAttributes(
                status = this.status.name,
                requestType = this.type.name,
                validTo = this.validTo.toTimeZoneOffsetString(),
                createdAt = this.createdAt.toTimeZoneOffsetString(),
                updatedAt = this.updatedAt.toTimeZoneOffsetString(),
            ),
            relationships = AuthorizationRequestResponseRelationships(
                requestedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.requestedBy.type.name,
                        id = this.requestedBy.id
                    )
                ),
                requestedFrom = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.requestedFrom.type.name,
                        id = this.requestedFrom.id
                    )
                ),
                requestedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.requestedTo.type.name,
                        id = this.requestedTo.id
                    )
                ),
            ),
            meta = JsonApiResourceMetaMap(
                buildMap {
                    this@toCreateResponse.properties.forEach { prop ->
                        put(prop.key, prop.value)
                    }
                }
            ),
            links = AuthorizationRequestResponseLinks(
                self = "${REQUESTS_PATH}/${this.id}"
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", this@toCreateResponse.createdAt.toTimeZoneOffsetString())
            }
        )
    )
