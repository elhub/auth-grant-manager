package no.elhub.auth.features.grants

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Authorization grant")
data class AuthorizationGrant (
    @field:Schema(description = "Grant ID", example = "123e4567-e89b-12d3-a456-426614174000")
    val id: UUID,
    @field:Schema(description = "User ID", example = "223e4567-e89b-12d3-a456-426614174001")
    val userId: UUID,
    @field:Schema(description = "Scope of access", example = "read:metering-data")
    val scope: String,
    @field:Schema(description = "Timestamp when the grant was created")
    val createdAt: Instant
)
