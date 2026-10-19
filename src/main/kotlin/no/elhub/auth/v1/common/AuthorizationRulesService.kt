package no.elhub.auth.v1.common

import no.elhub.auth.common.auth.AuthorizationParty

interface AuthorizationRulesService {
    suspend fun canActOnBehalfOf(
        representative: AuthorizationParty,
        principal: AuthorizationParty,
    ): Boolean
}
