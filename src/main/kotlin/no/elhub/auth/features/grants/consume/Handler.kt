package no.elhub.auth.features.grants.consume

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository
) {
    suspend operator fun invoke(command: ConsumeCommand): Either<ConsumeError, AuthorizationGrant> = either {
        ensure(command.authorizedParty.type == PartyType.System) {
            ConsumeError.NotAuthorized
        }
        ensure(command.newStatus == Status.Exhausted) {
            ConsumeError.IllegalTransitionError
        }

        repo.update(command.grantId, command.newStatus)
            .mapLeft { error ->
                when (error) {
                    is RepositoryWriteError.ConflictError -> ConsumeError.IllegalStateError
                    is RepositoryWriteError.ExpiredError -> ConsumeError.ExpiredError
                    else -> ConsumeError.PersistenceError
                }
            }
            .bind()
    }
}
