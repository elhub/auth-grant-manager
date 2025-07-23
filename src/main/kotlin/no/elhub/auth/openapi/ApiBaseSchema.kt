package no.elhub.auth.openapi

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "The top-level links object **may** contain the following members: self, related, pagination links. Currently, only self is defined.",
    type = "object",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
data class TopLevelLinks(
    @field:Schema(
        description = "The link that generated the current JSON document.",
        example = "https://api.elhub.no/example-endpoint/"
    )
    val self: String
)

@Schema(
    description = "Meta-information for the JSON document.",
    title = "object",
)
data class TopLevelMeta(
    @field:Schema(
        description = "The date and time when the document was created.",
        example = "2023-10-01T12:00:00Z"
    )
    val createdAt: String
)

@Schema
data class RelationshipData(
    @field:Schema
    val data: RelationshipInnerData
)

@Schema
data class RelationshipInnerData(
    @field:Schema(example = "Person")
    val type: String,
    @field:Schema(example = "12345678901")
    val id: String
)
