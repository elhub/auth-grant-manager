package no.elhub.auth.features.grants

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import no.elhub.auth.openapi.RelationshipData
import no.elhub.auth.openapi.TopLevelLinks
import no.elhub.auth.openapi.TopLevelMeta

@Schema(description = "JSON:API Authorization Grant Response")
data class AuthorizationGrantResponse(
    @field:Schema
    val data: AuthorizationGrantObject,
    @field:Schema
    val links: TopLevelLinks,
    @field:Schema
    val meta: TopLevelMeta
)

@Schema(description = "JSON:API Authorization Grant List Response")
data class AuthorizationGrantListResponse(
    @field:ArraySchema(
        schema = Schema(implementation = AuthorizationGrantObject::class),
        arraySchema = Schema(description = "List of authorization grants")
    )
    val data: List<AuthorizationGrantObject>,
    @field:Schema
    val links: TopLevelLinks,
    @field:Schema
    val meta: TopLevelMeta
)

@Schema
data class AuthorizationGrantObject(
    @field:Schema(example = "123e4567-e89b-12d3-a456-426614174000")
    val id: String,
    @field:Schema(example = "AuthorizationGrant")
    val type: String,
    @field:Schema
    val attributes: AuthorizationGrantAttributes,
    @field:Schema
    val relationships: AuthorizationGrantRelationships
)

@Schema
data class AuthorizationGrantAttributes(
    @field:Schema(example = "Active")
    val status: String,
    @field:Schema(example = "2025-07-22T08:26:13.420Z")
    val grantedAt: String,
    @field:Schema(example = "2025-07-22T08:26:13.420Z")
    val validFrom: String,
    @field:Schema(example = "2025-07-22T08:26:13.420Z")
    val validTo: String
)

@Schema
data class AuthorizationGrantRelationships(
    @field:Schema
    val grantedFor: RelationshipData,
    @field:Schema
    val grantedBy: RelationshipData,
    @field:Schema
    val grantedTo: RelationshipData
)
