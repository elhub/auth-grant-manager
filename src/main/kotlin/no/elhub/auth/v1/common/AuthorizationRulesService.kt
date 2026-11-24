package no.elhub.auth.v1.common

import no.elhub.auth.v1.common.auth.AuthorizationAgent

data class MeteringPointWriteAccessDecision(
    val authorized: Boolean,
    val unauthorizedMeteringPoints: List<String>?,
) {
    fun hasNoWriteAccessToAny(meteringPointIds: List<String>): Boolean =
        !authorized && unauthorizedMeteringPoints?.containsAll(meteringPointIds) == true

    fun hasWriteAccessToAll(): Boolean =
        authorized && unauthorizedMeteringPoints.orEmpty().isEmpty()
}

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

    suspend fun checkMeteringPointWriteAccess(
        principalElhubInternalId: String,
        meteringPointIds: List<String>,
    ): MeteringPointWriteAccessDecision
}
