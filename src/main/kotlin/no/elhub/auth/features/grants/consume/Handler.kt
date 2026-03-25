package no.elhub.auth.features.grants.consume

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.config.withTransaction
import no.elhub.auth.features.common.currentTimeUtc
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

        val updated = withTransaction {
            val originalGrant = repo.find(command.grantId)
                .mapLeft { ConsumeError.GrantNotFound }
                .bind()

            ensure(originalGrant.validTo >= currentTimeUtc()) {
                ConsumeError.ExpiredError
            }

            ensure(originalGrant.grantStatus == Status.Active) {
                ConsumeError.IllegalStateError
            }

            repo.update(command.grantId, command.newStatus)
                .mapLeft { ConsumeError.PersistenceError }
                .bind()
        }

        updated
    }
}
