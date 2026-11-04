package no.elhub.auth.v1.features.grants

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.repositories.GrantRepository
import java.util.UUID

class GetGrantHandler(
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
    ): AuthorizationGrant {
        val grant = grantRepository.find(id)

        if (authorizedParty.type != PartyType.System &&
            authorizedParty != grant.grantedTo &&
            authorizedParty != grant.principal &&
            authorizedParty != grant.grantedBy
        ) {
            throw GetGrantError.NotAuthorized()
        }

        return grant
    }
}
