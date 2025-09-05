package no.elhub.auth.features.grants.common

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.elhub.auth.features.grants.AuthorizationResourceType
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.PermissionType
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationParty
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

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
) : JsonApiRelationships

@Serializable
data class AuthorizationGrantScopeAttributes(
    val authorizedResourceType: AuthorizationResourceType,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: Instant
) : JsonApiAttributes

typealias AuthorizationGrantScopesResponse = JsonApiResponse.CollectionDocument<AuthorizationGrantScopeAttributes>

fun List<AuthorizationScope>.toResponse() =
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
        }
    )
