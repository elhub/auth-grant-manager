package no.elhub.auth.grantmanager.application.grants.createGrant

import no.elhub.auth.grantmanager.application.common.interfaces.IGrantRepository
import no.elhub.auth.grantmanager.domain.models.AuthorizationGrant
import java.util.UUID
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Handler(val repo: IGrantRepository) {
    fun handle(command: Command) {
        repo.create(
            AuthorizationGrant(
                UUID.randomUUID(),
                command.grantedFor,
                command.grantedBy,
                command.grantedTo,
                now(),
                command.validFrom ?: now(),
                command.validTo
            )
        )
    }
}
