import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.presentation.model.AuthorizationResourceType
import no.elhub.auth.grantmanager.presentation.model.AuthorizationScope
import no.elhub.auth.grantmanager.presentation.model.PermissionType
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject

@Serializable
data class AuthorizationGrantScopeAttributes(
    val authorizedResourceType: AuthorizationResourceType,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: Instant
) : JsonApiAttributes

typealias AuthorizationScopesResponse = JsonApiResponse.CollectionDocument<AuthorizationGrantScopeAttributes>

fun List<AuthorizationScope>.toGetAuthorizationGrantScopeResponse(): AuthorizationScopesResponse = AuthorizationScopesResponse(
    data = this.map { authorizationScope ->
        val attributes = AuthorizationGrantScopeAttributes(
            authorizedResourceType = authorizationScope.authorizedResourceType,
            authorizedResourceId = authorizationScope.authorizedResourceId,
            permissionType = authorizationScope.permissionType,
            createdAt = authorizationScope.createdAt
        )

        JsonApiResponseResourceObject(
            type = "AuthorizationScope",
            id = authorizationScope.id.toString(),
            attributes = attributes
        )
    }
)
