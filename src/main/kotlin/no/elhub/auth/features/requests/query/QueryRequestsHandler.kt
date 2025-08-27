package no.elhub.auth.features.requests.query

import arrow.core.Either
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProblem
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class QueryRequestsHandler(private val repo: RequestRepository) {
    operator fun invoke(query: QueryRequestsQuery): Either<AuthorizationRequestProblem, List<AuthorizationRequest>> =
        repo.findAll()
}
