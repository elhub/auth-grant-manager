package no.elhub.auth.features.grants.consume

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: GrantRepository
) {
    operator fun invoke(command: ConsumeCommand): Either<ConsumeError, AuthorizationGrant> {
        val updated = repo.update(command.grantId, command.newStatus)
            .getOrElse { return ConsumeError.PersistenceError.left() }

        return updated.right()
    }
}
