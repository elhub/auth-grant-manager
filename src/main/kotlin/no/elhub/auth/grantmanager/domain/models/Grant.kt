package no.elhub.auth.grantmanager.domain.models

import java.util.UUID
import java.time.Instant

class Grant(
    id: UUID,
    val grantedAt: Instant,
    val validFrom: Instant,
    val validTo: Instant,
) : AuditableEntity(id) {
    val active = Instant.now() in validFrom..validTo
    val expired = Instant.now() > validTo
    // TODO: Exhausted?
    // TODO: Revoked?
}
