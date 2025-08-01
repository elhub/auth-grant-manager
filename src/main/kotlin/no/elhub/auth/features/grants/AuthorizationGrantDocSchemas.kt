package no.elhub.auth.features.grants

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import no.elhub.auth.openapi.RelationshipData
import no.elhub.auth.openapi.TopLevelLinks
import no.elhub.auth.openapi.TopLevelMeta
import no.elhub.auth.openapi.TopLevelPropertyConstraint

@Schema(
    description = "Schema for returning a single authorization grant object in the Elhub authorization manager.",
    type = "object",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    allOf = [TopLevelPropertyConstraint::class]
)
data class AuthorizationGrantResponse(
    @field:Schema(
        description = "Schema for authorization grant data object.",
        type = "object"
    )
    val data: AuthorizationGrantObject,
    @field:Schema
    val links: TopLevelLinks,
    @field:Schema
    val meta: TopLevelMeta
)

@Schema(
    description = "Schema for returning a collection of authorization grants objects in the Elhub authorization manager.",
    type = "object",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    allOf = [TopLevelPropertyConstraint::class]
)
data class AuthorizationGrantListResponse(
    @field:ArraySchema(
        schema = Schema(implementation = AuthorizationGrantObject::class),
        arraySchema = Schema(description = "List of authorization grants"),
        uniqueItems = true
    )
    val data: List<AuthorizationGrantObject>,
    @field:Schema
    val links: TopLevelLinks,
    @field:Schema
    val meta: TopLevelMeta
)

data class AuthorizationGrantObject(
    @field:Schema(example = "123e4567-e89b-12d3-a456-426614174000")
    val id: String,

    @field:Schema(
        type = "string",
        description = "The type of the resource object.",
        _const = "AuthorizationGrant"
    )
    val type: String,

    @field:Schema(
        description = "The attributes of the authorization grant.",
        type = "object"
    )
    val attributes: AuthorizationGrantAttributes,

    @field:Schema
    val relationships: AuthorizationGrantRelationships
)

data class AuthorizationGrantAttributes(
    @field:Schema(
        description = "The status of the authorization grant.",
        type = "string",
        example = "Active",
        allowableValues = ["Active", "Exhausted", "Expired", "Revoked"]
    )
    val status: String,

    @field:Schema(
        type = "string",
        format = "date-time",
        description = "The date and time when the authorization is granted at.",
        example = "2025-07-22T08:26:13.420Z"
    )
    val grantedAt: String,

    @field:Schema(
        type = "string",
        format = "date-time",
        description = "The date and time when the authorization grant is valid from.",
        example = "2025-07-22T08:26:13.420Z"
    )
    val validFrom: String,

    @field:Schema(
        type = "string",
        format = "date-time",
        description = "The date and time when the authorization grant is valid to.",
        example = "2025-07-22T08:26:13.420Z"
    )
    val validTo: String
)

@Schema(
    type = "object",
    description = "The related entities of the authorization grant (person and organizations)."
)
data class AuthorizationGrantRelationships(
    @field:Schema
    val grantedFor: RelationshipData,
    @field:Schema
    val grantedBy: RelationshipData,
    @field:Schema
    val grantedTo: RelationshipData
)
