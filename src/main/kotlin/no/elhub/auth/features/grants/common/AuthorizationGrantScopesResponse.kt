package no.elhub.auth.features.grants.common

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.scope.AuthorizationScope
import no.elhub.auth.features.common.scope.ElhubResource
import no.elhub.auth.features.common.scope.PermissionType
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject

@Serializable
data class GrantResponseAttributes(
    val status: String,
    val grantedAt: String,
    val validFrom: String,
    val validTo: String,
) : JsonApiAttributes

@Serializable
data class GrantRelationships(
    val grantedFor: JsonApiRelationshipToOne,
    val grantedBy: JsonApiRelationshipToOne,
    val grantedTo: JsonApiRelationshipToOne,
    val source: JsonApiRelationshipToOne,
) : JsonApiRelationships

@Serializable
data class AuthorizationGrantScopeAttributes(
    val authorizedResourceType: ElhubResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: String
) : JsonApiAttributes

typealias AuthorizationGrantScopesResponse = JsonApiResponse.CollectionDocument<AuthorizationGrantScopeAttributes>

fun List<AuthorizationScope>.toResponse(grantId: String) =
    AuthorizationGrantScopesResponse(
        data = this.map { authorizationScope ->
            val attributes = AuthorizationGrantScopeAttributes(
                authorizedResourceType = authorizationScope.authorizedResourceType,
                authorizedResourceId = authorizationScope.authorizedResourceId,
                permissionType = authorizationScope.permissionType,
                createdAt = authorizationScope.createdAt
            )

            JsonApiResponseResourceObject(
                type = (AuthorizationScope::class).simpleName ?: "AuthorizationScope",
                id = authorizationScope.id.toString(),
                attributes = attributes
            )
        },
        links = JsonApiLinks.ResourceObjectLink(
            self = "authorization-grants/{id}/scopes"
        )
    )
