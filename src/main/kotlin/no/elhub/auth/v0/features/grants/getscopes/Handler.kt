package no.elhub.auth.v0.features.grants.getscopes

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.v0.features.common.QueryError
import no.elhub.auth.v0.features.common.RepositoryReadError
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.features.grants.AuthorizationScope
import no.elhub.auth.v0.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository,
) {
    suspend operator fun invoke(query: Query): Either<QueryError, List<AuthorizationScope>> = either {
        val grant = repo.find(query.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        val authorizedParty = query.authorizedParty

        ensure(authorizedParty.type == PartyType.System || authorizedParty == grant.grantedTo || authorizedParty == grant.grantedFor) {
            QueryError.NotAuthorizedError
        }

        repo.findScopes(query.id)
            .mapLeft { error ->
                when (error) {
                    RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }
            .bind()
    }
}
