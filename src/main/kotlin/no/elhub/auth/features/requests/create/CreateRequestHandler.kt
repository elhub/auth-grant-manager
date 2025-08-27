package no.elhub.auth.features.requests.create

import arrow.core.Either
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProblem
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class CreateRequestHandler(private val repo: RequestRepository) {
    operator fun invoke(command: CreateRequestCommand): Either<AuthorizationRequestProblem, AuthorizationRequest> =
        repo.insert(command.type, command.requester, command.requestee)
}
