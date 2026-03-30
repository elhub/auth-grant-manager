package no.elhub.auth.features.grants.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository,
) {
    suspend operator fun invoke(query: Query): Either<QueryError, List<AuthorizationGrant>> = either {
        repo.findAll(query.authorizedParty)
            .mapLeft {
                when (it) {
                    RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()
    }
}
