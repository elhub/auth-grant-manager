package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.grants.AuthorizationGrant

class GetGrantHandler(private val repo: GrantRepository) {
    operator fun invoke(query: GetGrantQuery): Either<QueryError, AuthorizationGrant> =
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
