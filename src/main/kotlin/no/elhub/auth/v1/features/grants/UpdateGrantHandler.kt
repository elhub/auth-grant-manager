package no.elhub.auth.v1.features.grants

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
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
    ): AuthorizationGrant {
        if (authorizedParty.type != PartyType.System) {
            throw UpdateGrantError.NotAuthorized()
        }

        if (status != AuthorizationGrantStatus.Revoked && status != AuthorizationGrantStatus.Exhausted) {
            throw UpdateGrantError.InvalidTransition()
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
