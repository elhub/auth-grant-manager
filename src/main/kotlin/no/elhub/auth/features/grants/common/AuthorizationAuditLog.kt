package no.elhub.auth.features.grants.common

import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizationAuditLog(
    val grantId: UUID,
    val changedAt: OffsetDateTime,
    val changedBy: String,
    val valueChanged: String,
    val valueBefore: String?,
    val valueAfter: String?,
    val message: String? = null,
)
