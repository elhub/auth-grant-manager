package no.elhub.auth.v1.features.grants.get.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.features.common.dto.ResponseGrantedScope
import no.elhub.auth.v1.features.common.dto.ResponseRelationship
import no.elhub.auth.v1.features.common.dto.ResponseResourceIdentifier
import no.elhub.auth.v1.features.common.dto.toApprovalRelationship
import no.elhub.auth.v1.features.common.dto.toResponse
import no.elhub.auth.v1.features.common.dto.toResponseIdentifier
import no.elhub.auth.v1.features.common.dto.toResponseRelationship
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import kotlin.time.Instant

private const val GRANTS_PATH = "/access/v1/authorization-grants"

typealias JsonApiGetAuthorizationGrantResponse = JsonApiResponse.Document<AuthorizationGrantResource>

@Serializable
data class AuthorizationGrantResource(
    val id: String,
    val type: String,
    val attributes: AuthorizationGrantAttributes,
    val relationships: AuthorizationGrantRelationships,
    val links: AuthorizationGrantLinks,
)

@Serializable
data class AuthorizationGrantAttributes(
    val status: AuthorizationGrantStatus,
    val scopes: List<ResponseGrantedScope>,
    val validFrom: Instant,
    val validTo: Instant,
    val grantedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class AuthorizationGrantRelationships(
    val principal: ResponseRelationship,
    val grantedBy: ResponseRelationship,
    val grantedTo: ResponseRelationship,
    val approvedBy: ResponseRelationship,
    val source: ResponseRelationship,
)

@Serializable
data class AuthorizationGrantLinks(
    val self: String,
)

fun AuthorizationGrant.toGetResponse(): JsonApiGetAuthorizationGrantResponse {
    val identifier = toResponseIdentifier()
    return JsonApiGetAuthorizationGrantResponse(
        data = AuthorizationGrantResource(
            id = identifier.id,
            type = identifier.type,
            attributes = AuthorizationGrantAttributes(
                status = status,
                scopes = scopes.map(GrantedAuthorizationScope::toResponse),
                validFrom = validFrom,
                validTo = validTo,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            relationships = AuthorizationGrantRelationships(
                principal = principal.toResponseRelationship(),
                grantedBy = grantedBy.toResponseRelationship(),
                grantedTo = grantedTo.toResponseRelationship(),
                approvedBy = approvedBy.toApprovalRelationship(),
                source = ResponseRelationship(
                    data = ResponseResourceIdentifier(
                        type = when (sourceType) {
                            AuthorizationGrantSourceType.Document -> "AuthorizationDocument"
                            AuthorizationGrantSourceType.Request -> "AuthorizationRequest"
                        },
                        id = sourceId.toString(),
                    ),
                ),
            ),
            links = AuthorizationGrantLinks(
                self = "$GRANTS_PATH/$id",
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink(self = "$GRANTS_PATH/$id"),
        meta = buildJsonObject {
            put("createdAt", createdAt.toString())
        },
    )
}
