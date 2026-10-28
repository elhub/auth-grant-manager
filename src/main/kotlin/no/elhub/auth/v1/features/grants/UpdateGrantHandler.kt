package no.elhub.auth.v1.features.grants

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
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
        authorizedParty: AuthorizationParty,
        status: AuthorizationGrantStatus,
        type: AuthorizationGrantType,
    ): AuthorizationGrant {
        if (authorizedParty.type != PartyType.System && status != AuthorizationGrantStatus.Revoked) {
            throw UpdateGrantError.NotAuthorized("${authorizedParty.type} cannot update authorization grant to status $status")
        }

        val grant = grantRepository.find(id)
        if (authorizedParty.type != PartyType.System && authorizedParty != grant.principal && authorizedParty != grant.grantedBy) {
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
