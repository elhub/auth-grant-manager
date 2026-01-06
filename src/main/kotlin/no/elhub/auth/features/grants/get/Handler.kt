package no.elhub.auth.features.grants.get

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.Constants
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.PartyType
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

        val authorizedParty = query.authorizedParty

        when (authorizedParty.type) {
            PartyType.System -> ensure(authorizedParty.resourceId == Constants.CONSENT_MANAGEMENT_OSB_ID) {
                QueryError.NotAuthorizedError
            }

            else -> ensure(
                authorizedParty == grant.grantedTo ||
                    authorizedParty == grant.grantedFor
            ) {
                QueryError.NotAuthorizedError
            }
        }

        grant
    }
}
