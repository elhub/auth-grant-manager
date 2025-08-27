package no.elhub.auth.features.requests.get

import arrow.core.Either
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProblem
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class GetRequestHandler(private val repo: RequestRepository) {
    operator fun invoke(command: GetRequestQuery): Either<AuthorizationRequestProblem, AuthorizationRequest> =
        repo.find(command.id)
}
