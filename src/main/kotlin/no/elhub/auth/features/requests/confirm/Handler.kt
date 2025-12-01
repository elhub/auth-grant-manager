package no.elhub.auth.features.requests.confirm

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository

class Handler(
    private val repo: RequestRepository
) {
    operator fun invoke(command: ConfirmCommand): Either<ConfirmError, AuthorizationRequest> {
        val updated = repo.confirm(command.requestId, command.newStatus)
            .getOrElse { return ConfirmError.PersistenceError.left() }

        // TODO add state-transition validation in another PR
        // TODO add authorization check in another PR

        return updated.right()
    }
}
