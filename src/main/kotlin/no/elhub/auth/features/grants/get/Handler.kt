package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository
) {
    operator fun invoke(query: Query): Either<QueryError, AuthorizationGrant> = either {
        val grant = repo.find(query.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        when (query) {
            is Query.GrantedFor -> {
                ensure(grant.grantedFor == query.grantedFor) { QueryError.NotAuthorizedError }
            }

            is Query.GrantedTo ->
                ensure(grant.grantedTo == query.grantedTo) { QueryError.NotAuthorizedError }
        }
        grant
    }
}
