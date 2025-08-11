package no.elhub.auth.grantmanager.domain.models

import no.elhub.auth.grantmanager.domain.valueobjects.OrganizationNumber
import java.util.UUID

class Supplier(
    id: UUID,
    val organizationNumber: OrganizationNumber,
    val name: String,
) : AuditableEntity(id)
