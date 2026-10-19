package no.elhub.auth.v1.features.grants

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationGrant
import java.util.UUID

interface GetGrantHandler {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
    ): AuthorizationGrant
}
