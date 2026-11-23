package no.elhub.auth.v1.features.grants

import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.logging.errorName
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryWriteError
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

class UpdateGrantHandler(
    private val grantRepository: GrantRepository,
) {
    private val logger = LoggerFactory.getLogger(UpdateGrantHandler::class.java)

    suspend operator fun invoke(
        id: Uuid,
        authorizedAgent: AuthorizationAgent,
        status: AuthorizationGrantStatus,
        type: AuthorizationGrantType,
    ): AuthorizationGrant {
        val updatedGrant = try {
            update(id, authorizedAgent, status, type)
        } catch (error: UpdateGrantError) {
            logger.warn(
                "event=authorization_grant_update_failed grantId={} targetStatus={} reason={}",
                id,
                status,
                error.errorName,
            )
            throw error
        }
        logger.info(
            "event=authorization_grant_updated grantId={} grantType={} status={}",
            updatedGrant.id,
            updatedGrant.type,
            updatedGrant.status,
        )
        return updatedGrant
    }

    private suspend fun update(
        id: Uuid,
        authorizedAgent: AuthorizationAgent,
        status: AuthorizationGrantStatus,
        type: AuthorizationGrantType,
    ): AuthorizationGrant {
        val isSystem = authorizedAgent.type == AgentType.System
        if (isSystem && status == AuthorizationGrantStatus.Revoked) {
            throw UpdateGrantError.NotAuthorized("System cannot revoke an AuthorizationGrant.")
        }
        if (!isSystem && status != AuthorizationGrantStatus.Revoked) {
            throw UpdateGrantError.NotAuthorized("${authorizedAgent.type} cannot update authorization grant to status $status")
        }

        val grant = grantRepository.find(id)
        when (status) {
            AuthorizationGrantStatus.Revoked -> {
                if (authorizedAgent != grant.principal && authorizedAgent != grant.grantedTo) {
                    throw UpdateGrantError.NotAuthorized("Only the principal or grantedTo may revoke an AuthorizationGrant.")
                }
            }

            AuthorizationGrantStatus.Exhausted -> Unit

            else -> throw UpdateGrantError.InvalidTransition()
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
