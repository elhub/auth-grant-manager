package no.elhub.auth.features.grants.update

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
    suspend operator fun invoke(command: UpdateCommand): Either<UpdateError, AuthorizationGrant> = either {
        ensure(command.authorizedParty.type == PartyType.System) {
            UpdateError.NotAuthorized
        }
        ensure(command.newStatus == Status.Exhausted || command.newStatus == Status.Revoked) {
            UpdateError.IllegalTransitionError(
                "Cannot update authorization grant to status '${command.newStatus}'. Allowed statuses are 'Exhausted', 'Revoked'."
            )
        }

        repo.update(command.grantId, command.newStatus)
            .mapLeft { error ->
                when (error) {
                    is RepositoryWriteError.NotFoundError -> UpdateError.GrantNotFound
                    is RepositoryWriteError.ConflictError -> UpdateError.IllegalStateError
                    is RepositoryWriteError.ExpiredError -> UpdateError.ExpiredError
                    else -> UpdateError.PersistenceError
                }
            }
            .bind()
    }
}
