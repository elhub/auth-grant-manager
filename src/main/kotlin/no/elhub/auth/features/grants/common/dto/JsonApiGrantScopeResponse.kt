package no.elhub.auth.features.grants.common.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToMany
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class ScopeResponseAttributes(
    val permissionType: AuthorizationScope.PermissionType,
    val createdAt: String
) : JsonApiAttributes

@Serializable
data class ScopeResponseRelationships(
    val authorizedResources: JsonApiRelationshipToMany
) : JsonApiRelationships

typealias AuthorizationGrantScopesResponse = JsonApiResponse.CollectionDocumentWithRelationships<
    ScopeResponseAttributes,
    ScopeResponseRelationships,
    >

fun List<AuthorizationScope>.toResponse(grantId: String) =
    AuthorizationGrantScopesResponse(
        data = this.map { authorizationScope ->
            JsonApiResponseResourceObjectWithRelationships(
                id = authorizationScope.id.toString(),
                type = (AuthorizationScope::class).simpleName ?: "AuthorizationScope",
                attributes = ScopeResponseAttributes(
                    permissionType = authorizationScope.permissionType,
                    createdAt = authorizationScope.createdAt.toTimeZoneOffsetString()
                ),
                relationships = ScopeResponseRelationships(
                    authorizedResources = JsonApiRelationshipToMany(
                        data = listOf(
                            JsonApiRelationshipData(
                                id = authorizationScope.authorizedResourceId,
                                type = authorizationScope.authorizedResourceType.name
                            )
                        )
                    )
                ),
                meta = JsonApiMeta(
                    buildJsonObject {
                        put("createdAt", authorizationScope.createdAt.toTimeZoneOffsetString())
                    }
                )
            )
        },
        links = JsonApiLinks.ResourceObjectLink("$GRANTS_PATH/$grantId/scopes"),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
            }
        )
    )
