package no.elhub.auth.grantmanager.domain.models

import java.time.Instant
import java.util.UUID

open class AuditableEntity(val id: UUID) {
    val createdAt: Instant = Instant.now()
}
