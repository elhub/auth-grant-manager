package no.elhub.auth.v0.features.grants.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.QueryError
import no.elhub.auth.v0.features.common.RepositoryReadError
import no.elhub.auth.v0.features.grants.AuthorizationGrant
import no.elhub.auth.v0.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository,
) {
    suspend operator fun invoke(query: Query): Either<QueryError, Page<AuthorizationGrant>> = either {
        repo.findAll(query.authorizedParty, query.pagination)
            .mapLeft {
                when (it) {
                    RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()
    }
}
