package no.elhub.auth.features.grants.common.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToMany
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class GrantResponseAttributes(
    val status: String,
    val grantedAt: String,
    val validFrom: String,
    val validTo: String,
    val createdAt: String?,
    val updatedAt: String?
) : JsonApiAttributes

@Serializable
data class GrantResponseRelationShips(
    val grantedFor: JsonApiRelationshipToOne,
    val grantedBy: JsonApiRelationshipToOne,
    val grantedTo: JsonApiRelationshipToOne,
    val source: JsonApiRelationshipToOne,
    val scopes: JsonApiRelationshipToMany
) : JsonApiRelationships

typealias SingleGrantResponse = JsonApiResponse.SingleDocumentWithRelationships<
    GrantResponseAttributes,
    GrantResponseRelationShips,
    >

typealias CollectionGrantResponse = JsonApiResponse.CollectionDocumentWithRelationships<
    GrantResponseAttributes,
    GrantResponseRelationShips,
    >

fun AuthorizationGrant.toSingleGrantResponse() =
    SingleGrantResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationGrant",
            id = this.id.toString(),
            attributes = GrantResponseAttributes(
                status = this.grantStatus.name,
                grantedAt = this.grantedAt.toTimeZoneOffsetString(),
                validFrom = this.validFrom.toTimeZoneOffsetString(),
                validTo = this.validTo.toTimeZoneOffsetString(),
                createdAt = this.createdAt?.toTimeZoneOffsetString(),
                updatedAt = this.updatedAt?.toTimeZoneOffsetString(),
            ),
            relationships = GrantResponseRelationShips(
                grantedFor = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.grantedFor.type.name,
                        id = this.grantedFor.id
                    )
                ),
                grantedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.grantedBy.type.name,
                        id = this.grantedBy.id
                    )
                ),
                grantedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = this.grantedTo.type.name,
                        id = this.grantedTo.id
                    )
                ),
                source = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.sourceId.toString(),
                        type = when (this.sourceType) {
                            AuthorizationGrant.SourceType.Document -> "AuthorizationDocument"
                            AuthorizationGrant.SourceType.Request -> "AuthorizationRequest"
                        }
                    ),
                    links = JsonApiLinks.RelationShipLink(
                        self = when (this.sourceType) {
                            AuthorizationGrant.SourceType.Document -> "$DOCUMENTS_PATH/$sourceId"
                            AuthorizationGrant.SourceType.Request -> "$REQUESTS_PATH/$sourceId"
                        }
                    )
                ),
                scopes = JsonApiRelationshipToMany(
                    data = this.scopeIds.map { scope ->
                        JsonApiRelationshipData(
                            id = scope.toString(),
                            type = "AuthorizationScope"
                        )
                    }
                )
            ),
            links = JsonApiLinks.ResourceObjectLink("$GRANTS_PATH/${this.id}"),
        ),
        links = JsonApiLinks.ResourceObjectLink("$GRANTS_PATH/${this.id}"),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
            }
        )
    )

fun List<AuthorizationGrant>.toCollectionGrantResponse() =
    CollectionGrantResponse(
        data = this.map { grant ->
            JsonApiResponseResourceObjectWithRelationships(
                type = "AuthorizationGrant",
                id = grant.id.toString(),
                attributes = GrantResponseAttributes(
                    status = grant.grantStatus.name,
                    grantedAt = grant.grantedAt.toTimeZoneOffsetString(),
                    validFrom = grant.validFrom.toTimeZoneOffsetString(),
                    validTo = grant.validTo.toTimeZoneOffsetString(),
                    createdAt = grant.createdAt?.toTimeZoneOffsetString(),
                    updatedAt = grant.updatedAt?.toTimeZoneOffsetString(),
                ),
                relationships = GrantResponseRelationShips(
                    grantedFor = JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            type = grant.grantedFor.type.name,
                            id = grant.grantedFor.id
                        )
                    ),
                    grantedBy = JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            type = grant.grantedBy.type.name,
                            id = grant.grantedBy.id
                        )
                    ),
                    grantedTo = JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            type = grant.grantedTo.type.name,
                            id = grant.grantedTo.id
                        )
                    ),
                    source = JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = grant.sourceId.toString(),
                            type = when (grant.sourceType) {
                                AuthorizationGrant.SourceType.Document -> "AuthorizationDocument"
                                AuthorizationGrant.SourceType.Request -> "AuthorizationRequest"
                            }
                        ),
                        links = JsonApiLinks.RelationShipLink(
                            self = when (grant.sourceType) {
                                AuthorizationGrant.SourceType.Document -> "$DOCUMENTS_PATH/${grant.sourceId}"
                                AuthorizationGrant.SourceType.Request -> "$REQUESTS_PATH/${grant.sourceId}"
                            }
                        )
                    ),
                    scopes = JsonApiRelationshipToMany(
                        data = grant.scopeIds.map { scope ->
                            JsonApiRelationshipData(
                                id = scope.toString(),
                                type = "AuthorizationScope"
                            )
                        }
                    )
                ),
                links = JsonApiLinks.ResourceObjectLink("$GRANTS_PATH/${grant.id}"),
            )
        },
        links = JsonApiLinks.ResourceObjectLink(GRANTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
            }
        )
    )
