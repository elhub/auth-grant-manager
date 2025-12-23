package no.elhub.auth.features.grants.common.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

// TODO use this response object for get and query to avoid duplications

@Serializable
data class GrantResponseAttributes(
    val status: String,
    val grantedAt: String,
    val validFrom: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class GrantResponseRelationShips(
    val grantedFor: JsonApiRelationshipToOne,
    val grantedBy: JsonApiRelationshipToOne,
    val grantedTo: JsonApiRelationshipToOne,
) : JsonApiRelationships

typealias GrantResponse = JsonApiResponse.SingleDocumentWithRelationships<
    GrantResponseAttributes,
    GrantResponseRelationShips,
    >

fun AuthorizationGrant.toGrantResponse() =
    GrantResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationGrant",
            id = this.id.toString(),
            attributes = GrantResponseAttributes(
                status = this.grantStatus.name,
                grantedAt = this.grantedAt.toTimeZoneOffsetString(),
                validFrom = this.validFrom.toTimeZoneOffsetString(),
                validTo = this.validTo.toTimeZoneOffsetString()
            ),
            relationships = GrantResponseRelationShips(
                grantedFor = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.grantedFor.type.name,
                        id = this.grantedFor.resourceId
                    )
                ),
                grantedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.grantedBy.type.name,
                        id = this.grantedBy.resourceId
                    )
                ),
                grantedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.grantedTo.type.name,
                        id = this.grantedTo.resourceId
                    )
                ),
            ),
            links = JsonApiLinks.ResourceObjectLink("https://api.elhub.no/authorization-grants${this.id}"),
        ),
        links = JsonApiLinks.ResourceObjectLink("https://api.elhub.no/authorization-grants"),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
            }
        )
    )
