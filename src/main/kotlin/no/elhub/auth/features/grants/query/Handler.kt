package no.elhub.auth.features.grants.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository,
    private val partyService: PartyService,
) {
    suspend operator fun invoke(query: Query): Either<QueryError, List<AuthorizationGrant>> = either {
        val grantedToParty = partyService.resolve(query.grantedTo)
            .mapLeft { QueryError.IOError }
            .bind()
        repo.findAll(grantedToParty)
            .mapLeft {
                when (it) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()
    }
}
