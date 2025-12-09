package no.elhub.auth.features.requests.confirm

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository

class Handler(
    private val requestRepository: RequestRepository,
) {
    operator fun invoke(command: ConfirmCommand): Either<ConfirmError, AuthorizationRequest> = when (command.newStatus) {
        AuthorizationRequest.Status.Accepted -> {
            handleAcceptedRequest(command)
        }

        AuthorizationRequest.Status.Pending,
        AuthorizationRequest.Status.Expired, // TODO is Pending -> Expired allowed here?
        AuthorizationRequest.Status.Rejected // TODO is Pending -> Rejected allowed here?
        -> ConfirmError.UnsupportedStatusTransition.left()
    }

    private fun handleAcceptedRequest(command: ConfirmCommand): Either<ConfirmError, AuthorizationRequest> {
        val updatedAccepted = requestRepository.confirmRequest(command.requestId, command.newStatus)
            .getOrElse { return ConfirmError.PersistenceError.left() }

        return updatedAccepted.right()
    }
}
