package no.elhub.auth.v1.common

import no.elhub.auth.v1.common.auth.AuthorizationAgent

interface AuthorizationRulesService {
    suspend fun isApproverAllowed(
        approver: AuthorizationAgent,
        requestedOf: AuthorizationAgent,
        requestedPrincipal: AuthorizationAgent,
    ): Boolean

    suspend fun canActOnBehalfOf(
        requestedOf: AuthorizationAgent,
        requestedPrincipal: AuthorizationAgent,
    ): Boolean
}
