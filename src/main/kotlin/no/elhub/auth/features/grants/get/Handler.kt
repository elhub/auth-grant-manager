package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(private val repo: GrantRepository) {
    operator fun invoke(query: Query): Either<QueryError, AuthorizationGrant> =
        repo.find(query.id)
            .fold(
                { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError.left()
                        is RepositoryReadError.UnexpectedError -> QueryError.IOError.left()
                    }
                },
                { grant -> grant.right() }
            )
}
