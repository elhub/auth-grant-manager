package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest

import no.elhub.auth.grantmanager.domain.models.MeteringPoint
import java.time.Instant
import java.util.UUID

sealed class CreateRequestError {
    data class RequestExistsForMeteringPointError(val meteringPoint: MeteringPoint) : CreateRequestError()
    data class InvalidRequestExpiryDateError(val expiryDate: Instant) : CreateRequestError()
    data class MeterRetrievalError(val meteringPointId: UUID) : CreateRequestError()
    data class SupplierRetrievalError(val supplierId: UUID) : CreateRequestError()
    data class SystemError(val reason: String) : CreateRequestError()
}
