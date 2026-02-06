package no.elhub.auth.features.grants.consume

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.AuthorizationParties
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.common.GrantRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Handler(
    private val repo: GrantRepository
) {
    operator fun invoke(command: ConsumeCommand): Either<ConsumeError, AuthorizationGrant> = either {
        ensure(command.authorizedParty == AuthorizationParties.ConsentManagementSystem) {
            ConsumeError.NotAuthorized
        }

        ensure(command.newStatus == Status.Exhausted) {
            ConsumeError.IllegalTransitionError
        }

        val updated = transaction {
            val originalGrant = repo.find(command.grantId)
                .mapLeft { ConsumeError.GrantNotFound }
                .bind()

            ensure(originalGrant.validTo >= OffsetDateTime.now(ZoneOffset.UTC)) {
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
