package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository,
    private val partyService: PartyService
) {
    suspend operator fun invoke(query: Query): Either<QueryError, AuthorizationGrant> = either {
        val grant = repo.find(query.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        val grantedTo = partyService.resolve(partyIdentifier = query.grantedTo).mapLeft { QueryError.IOError }.bind()

        if (grant.grantedTo.resourceId != grantedTo.resourceId) {
            raise(QueryError.NotAuthorizedError)
        }

        grant
    }
}
