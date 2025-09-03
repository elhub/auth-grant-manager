package no.elhub.auth.features.grants.getScopes

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationScope

class GetGrantScopesHandler(private val repo: GrantRepository) {
    operator fun invoke(query: GetGrantScopesQuery): Either<QueryError, List<AuthorizationScope>> =
        repo
            .findScopes(query.id)
            .fold(
                { error ->
                    when (error) {
                        RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError.left()
                        RepositoryReadError.UnexpectedError -> QueryError.IOError.left()
                    }
                },
                { scopes -> scopes.right() }
            )
}
