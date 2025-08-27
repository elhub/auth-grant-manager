package no.elhub.auth.features.grants.query

import arrow.core.Either
import no.elhub.auth.features.grants.common.AuthorizationGrantProblem
import no.elhub.auth.features.grants.common.GrantRepository

class QueryGrantsHandler(private val repo: GrantRepository) {
    operator fun invoke(query: QueryGrantsQuery): Either<AuthorizationGrantProblem, GrantRepository.GrantsWithParties> =
        repo.findAll()
}
