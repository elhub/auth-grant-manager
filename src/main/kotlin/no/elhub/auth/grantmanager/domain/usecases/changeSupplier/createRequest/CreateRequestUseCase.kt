package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest.CreateRequestError.RequestExistsForMeteringPointError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest.CreateRequestError.MeterRetrievalError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest.CreateRequestError.SupplierRetrievalError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest.CreateRequestError.InvalidRequestExpiryDateError
import no.elhub.auth.grantmanager.domain.models.ChangeSupplierRequest
import no.elhub.auth.grantmanager.domain.repositories.DocumentRepository
import no.elhub.auth.grantmanager.domain.repositories.MeterRepository
import no.elhub.auth.grantmanager.domain.repositories.ChangeSupplierRequestRepository
import no.elhub.auth.grantmanager.domain.repositories.SupplierRepository
import no.elhub.auth.grantmanager.domain.services.SignedDocumentGenerator
import java.time.Instant
import java.util.UUID

class CreateRequestUseCase(
    val meterRepository: MeterRepository,
    val supplierRepository: SupplierRepository,
    val changeSupplierRequestRepository: ChangeSupplierRequestRepository,
    val documentRepository: DocumentRepository,
    val documentGenerator: SignedDocumentGenerator,
) {
    suspend operator fun invoke(command: CreateRequestCommand): Either<CreateRequestError, ChangeSupplierRequest> {
        val meteringPointId = UUID.fromString(command.meteringPointId)
        val meteringPoint = when (val meterResponse = meterRepository.get(meteringPointId)) {
            is Either.Left -> return MeterRetrievalError(meteringPointId).left()
            is Either.Right -> meterResponse.value
        }

        val supplierId = UUID.fromString(command.supplierId)
        val supplier = when (val supplierResponse = supplierRepository.get(supplierId)) {
            is Either.Left -> return SupplierRetrievalError(supplierId).left()
            is Either.Right -> supplierResponse.value
        }

        // Reject request creation if valid until date in the past
        val now = Instant.now()
        if (command.validUntil <= now) {
            return InvalidRequestExpiryDateError(command.validUntil).left()
        }

        // Reject request creation if a request already exists for this meter
        changeSupplierRequestRepository.getForMeteringPoint(meteringPoint)?.let {
            return RequestExistsForMeteringPointError(meteringPoint).left()
        }

        val changeSupplierRequest =
            ChangeSupplierRequest(UUID.randomUUID(), meteringPoint, supplier, command.validUntil)

        if (command.generateContract) {

            val contract = documentGenerator.generateAndSign(changeSupplierRequest)

            changeSupplierRequest.apply {
                this.contract = contract
            }

            documentRepository.create(contract)
        }

        changeSupplierRequestRepository.create(changeSupplierRequest)

        return changeSupplierRequest.right()
    }
}
