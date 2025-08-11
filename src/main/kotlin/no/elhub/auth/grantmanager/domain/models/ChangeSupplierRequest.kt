package no.elhub.auth.grantmanager.domain.models

import java.time.Instant
import java.util.UUID

class ChangeSupplierRequest(
    id: UUID,
    val meteringPoint: MeteringPoint,
    val requester: Supplier,
    val validUntil: Instant,
) : AuditableEntity(id) {
    val expired = Instant.now() > validUntil
    var grant: Grant? = null
    var contract: SignableDocument? = null
    val granted = this.grant != null || contract?.signed ?: false
}
