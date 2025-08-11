package no.elhub.auth.grantmanager.domain.models

import java.util.UUID

class MeteringPoint(
    id: UUID,
    val owner: Consumer)
    : AuditableEntity(id)
