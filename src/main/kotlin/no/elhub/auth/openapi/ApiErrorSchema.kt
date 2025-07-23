package no.elhub.auth.openapi

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "JSON Bad Request Error Response")
data class ApiErrorResponse(
    @field:ArraySchema(
        schema = Schema(implementation = Error::class),
        arraySchema = Schema(description = "Array of error objects")
    )
    val errors: List<Error>,
    @field:Schema(description = "Link members related to the primary data")
    val links: TopLevelLinks? = null,
    @field:Schema(description = "Meta information about the response")
    val meta: TopLevelMeta? = null
) {
    @Schema(description = "Error object")
    data class Error(
        @field:Schema(
            description = "The HTTP status code applicable to this problem, expressed as a string value", example = "400")
        val status: String,
        @field:Schema(description = "An application-specific error code, expressed as a string value", example = "invalid_input")
        val code: String? = null,
        @field:Schema(description = "A short, human-readable summary of the problem", example = "Invalid input")
        val title: String,
        @field:Schema(description = "A human-readable explanation specific to this occurrence of the problem", example = "The input field 'name' is required.")
        val detail: String
    )
}
