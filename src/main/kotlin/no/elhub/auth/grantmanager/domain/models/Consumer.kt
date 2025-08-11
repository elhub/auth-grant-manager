package no.elhub.auth.grantmanager.domain.models

import no.elhub.auth.grantmanager.domain.valueobjects.SocialSecurityNumber
import java.util.UUID

class Consumer(
    id: UUID,
    val ssn: SocialSecurityNumber,
    val name: String,
) : AuditableEntity(id)
