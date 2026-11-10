package no.elhub.auth.v1.features.grants

import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryWriteError
import java.util.UUID

class UpdateGrantHandler(
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedAgent: AuthorizationAgent,
        status: AuthorizationGrantStatus,
        type: AuthorizationGrantType,
    ): AuthorizationGrant {
        if (authorizedAgent.type != AgentType.System && status != AuthorizationGrantStatus.Revoked) {
            throw UpdateGrantError.NotAuthorized("${authorizedAgent.type} cannot update authorization grant to status $status")
        }

        val grant = grantRepository.find(id)
        if (authorizedAgent.type != AgentType.System && authorizedAgent != grant.principal && authorizedAgent != grant.grantedBy) {
            throw UpdateGrantError.NotAuthorized("Only the principal, grantedBy, or systems may revoke an AuthorizationGrant.")
        }

        if (status != AuthorizationGrantStatus.Revoked && status != AuthorizationGrantStatus.Exhausted) {
            throw UpdateGrantError.InvalidTransition()
        }

        if (type != grant.type) {
            throw UpdateGrantError.TypeMismatch()
        }

        return try {
            grantRepository.update(id, status)
        } catch (error: RepositoryWriteError.ConflictError) {
            throw UpdateGrantError.InvalidTransition()
        } catch (error: RepositoryWriteError.ExpiredError) {
            throw UpdateGrantError.Expired()
        }
    }
}
