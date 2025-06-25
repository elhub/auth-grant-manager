import kotlinx.serialization.Serializable
import no.elhub.auth.features.utils.Serializers
import no.elhub.auth.model.AuthorizationResourceType
import no.elhub.auth.model.AuthorizationScope
import no.elhub.auth.model.PermissionType
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject
import java.time.Instant

@Serializable
data class ScopeResponseAttributes(
    val authorizedResourceType: AuthorizationResourceType,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    @Serializable(with = Serializers.InstantSerializer::class)
    val createdAt: Instant
) : JsonApiAttributes

typealias AuthorizationScopesResponse = JsonApiResponse.CollectionDocument<ScopeResponseAttributes>

fun List<AuthorizationScope>.toGetAuthorizationScopesResponse(): AuthorizationScopesResponse = AuthorizationScopesResponse(
    data = this.map { authorizationScope ->
        val attributes = ScopeResponseAttributes(
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
