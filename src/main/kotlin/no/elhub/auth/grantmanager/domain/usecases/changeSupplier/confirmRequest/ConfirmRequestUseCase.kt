package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.grantmanager.domain.models.Grant
import no.elhub.auth.grantmanager.domain.models.SignableDocument
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest.ConfirmRequestError.RetrievalError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest.ConfirmRequestError.ContractNotSignedError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest.ConfirmRequestError.IncorrectContractError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest.ConfirmRequestError.NoContractAssociatedError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest.ConfirmRequestError.AlreadyGrantedError
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest.ConfirmRequestError.PendingContractSubmissionError
import no.elhub.auth.grantmanager.domain.repositories.ChangeSupplierRequestRepository
import java.time.Instant
import java.util.UUID

class ConfirmRequestUseCase(
    val requestRepository : ChangeSupplierRequestRepository,
) {
    suspend operator fun invoke(command: ConfirmRequestCommand) : Either<ConfirmRequestError, Unit> {

        val requestId = UUID.fromString(command.requestId)

        // TODO: Improve
        val request = when (val requestResponse = requestRepository.get(requestId)) {
            is Either.Left -> return RetrievalError(requestId).left()
            is Either.Right -> requestResponse.value
        }

        // Reject confirmation if the request has already been confirmed
        if (request.granted) {
            return AlreadyGrantedError().left()
        }

        // Reject confirmation if no updated contract has been provided, but a contract is awaiting signing
        if (command.contract == null && request.contract != null ) {
            return PendingContractSubmissionError().left()
        }

        command.contract?.let {

            val requestContract = request.contract ?: return NoContractAssociatedError().left()

            val providedContract = SignableDocument(requestContract.id, requestContract.title, it)

            // Reject confirmation if the contracts do not match
            if (providedContract != requestContract) {
                return IncorrectContractError().left()
            }

            // Reject confirmation if the provided contract has not been signed
            if (!providedContract.signed) {
                return ContractNotSignedError().left()
            }
        }

        // TODO: Retrieve validFrom & validTo from... the request?
        val now = Instant.now()
        request.grant = Grant(UUID.randomUUID(), now, now, now)

        requestRepository.confirmRequest(requestId)

        return Unit.right()
    }
}
