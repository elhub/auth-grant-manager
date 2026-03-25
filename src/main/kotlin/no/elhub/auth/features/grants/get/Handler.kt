package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository
) {
    suspend operator fun invoke(query: Query): Either<QueryError, AuthorizationGrant> = either {
        val grant = withTransaction {
            repo.find(query.id)
                .mapLeft { error ->
                    when (error) {
                        RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                        RepositoryReadError.UnexpectedError -> QueryError.IOError
                    }
                }.bind()
        }

        val authorizedParty = query.authorizedParty

        ensure(authorizedParty.type == PartyType.System || authorizedParty == grant.grantedTo || authorizedParty == grant.grantedFor) {
            QueryError.NotAuthorizedError
        }

        grant
    }
}
