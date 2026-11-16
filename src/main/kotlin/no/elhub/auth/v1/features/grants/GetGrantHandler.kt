package no.elhub.auth.v1.features.grants

import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.repositories.GrantRepository
import java.util.UUID

class GetGrantHandler(
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedAgent: AuthorizationAgent,
    ): AuthorizationGrant {
        val grant = grantRepository.find(id)

        if (authorizedAgent.type != AgentType.System &&
            authorizedAgent != grant.grantedTo &&
            authorizedAgent != grant.principal
        ) {
            throw GetGrantError.NotAuthorized()
        }

        return grant
    }
}
