package no.elhub.auth.grantmanager.domain.repositories

import no.elhub.auth.grantmanager.domain.models.ChangeSupplierRequest
import no.elhub.auth.grantmanager.domain.models.MeteringPoint
import java.util.UUID

interface ChangeSupplierRequestRepository : AuditableEntityRepository<ChangeSupplierRequest> {
    suspend fun getForMeteringPoint(meteringPoint: MeteringPoint): ChangeSupplierRequest?
    suspend fun confirmRequest(requestId: UUID)
}
