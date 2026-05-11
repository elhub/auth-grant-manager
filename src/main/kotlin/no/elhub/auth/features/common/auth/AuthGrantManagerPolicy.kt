package no.elhub.auth.features.common.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.plugin.dto.PdpError
import no.elhub.auth.plugin.dto.TokenInfo
import no.elhub.auth.plugin.dto.TokenPdpResult
import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.core.TokenPolicy
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AuthGrantManagerPolicy")

const val AUTHINFO_POLICY_ROUTE = "/v1/data/v3/token/authinfo"

object AuthGrantManagerPolicy : TokenPolicy<AuthGrantManagerPolicy.Request, AuthGrantManagerPolicy.Response>(
    key = "token.authinfo",
    route = AUTHINFO_POLICY_ROUTE,
) {
    @Serializable
    data class Request(
        @SerialName("SenderGLN")
        val senderGLN: String? = null,
        @SerialName("OnBehalfOfGLN")
        val onBehalfOfGLN: String? = null,
        @SerialName("OnBehalfOfOrganisationId")
        val onBehalfOfOrganisationId: String? = null,
    )

    @Serializable
    data class Response(
        override val tokenInfo: TokenInfo? = null,
        val authInfo: ResponseAuthInfo? = null,
        override val error: PdpError? = null,
    ) : TokenPdpResult

    @Serializable
    data class ResponseAuthInfo(
        val inputFailed: String? = null,
        val authorizedFunctions: List<ResponseAuthorizedFunction>? = null,
        val actingGLN: String? = null,
        val actingId: String? = null,
        val actingType: String? = null,
        val actingOrganisationNumber: String? = null,
        val error: String? = null,
    )

    @Serializable
    data class ResponseAuthorizedFunction(
        val functionCode: String? = null,
        val functionName: String? = null,
    )

    override val requestSerializer = Request.serializer()
    override val responsePayloadSerializer = Response.serializer()
}

/**
 * Resolves an [AuthorizationParty] from the PDP response.
 * Returns null if the party cannot be resolved (enforce will return false → 403).
 */
fun resolveAuthorizedParty(response: AuthGrantManagerPolicy.Response): AuthorizationParty? {
    return when (response.tokenInfo?.tokenType) {
        TokenType.MASKINPORTEN -> resolveMaskinportenParty(response)
        TokenType.ENDUSER -> resolveEndUserParty(response)
        TokenType.ELHUB_SERVICE -> resolveSystemParty(response)
        else -> {
            log.warn("Unexpected or missing tokenType={}", response.tokenInfo?.tokenType)
            null
        }
    }
}

private fun resolveMaskinportenParty(response: AuthGrantManagerPolicy.Response): AuthorizationParty? {
    val authInfo = response.authInfo
    if (authInfo?.inputFailed != null) {
        log.error("PDP input validation failed msg={}", authInfo.inputFailed)
        return null
    }
    val actingGLN = authInfo?.actingGLN
    if (actingGLN == null) {
        log.warn("PDP response missing actingGLN")
        return null
    }
    val authorizedFunctions = authInfo.authorizedFunctions?.takeIf { it.isNotEmpty() }
    if (authorizedFunctions == null) {
        log.warn("PDP response missing authorizedFunctions")
        return null
    }
    val hasBalanceSupplier = authorizedFunctions.any { it.functionName == "BalanceSupplier" }
    if (!hasBalanceSupplier) {
        log.warn("Unsupported authorizedFunctions={}", authorizedFunctions)
        return null
    }
    return AuthorizationParty(id = actingGLN, type = PartyType.OrganizationEntity)
        .also { log.info("Authorized party is $it") }
}

private fun resolveEndUserParty(response: AuthGrantManagerPolicy.Response): AuthorizationParty? {
    val authInfo = response.authInfo
    if (authInfo?.error != null) {
        log.warn("PDP authInfo error={}", authInfo.error)
        return null
    }
    return when (authInfo?.actingType?.trim()?.lowercase()?.ifBlank { null }) {
        "person" -> {
            val actingId = authInfo.actingId ?: run {
                log.warn("PDP response missing actingId for person")
                return null
            }
            AuthorizationParty(id = actingId, type = PartyType.Person)
        }
        "organisation" -> {
            val orgNumber = authInfo.actingOrganisationNumber ?: run {
                log.warn("PDP response missing actingOrganisationNumber")
                return null
            }
            AuthorizationParty(id = orgNumber, type = PartyType.Organization)
        }
        else -> {
            log.warn("Unsupported actingType={}", authInfo?.actingType)
            null
        }
    }.also { log.info("Authorized party is $it") }
}

private fun resolveSystemParty(response: AuthGrantManagerPolicy.Response): AuthorizationParty? {
    val partyId = response.tokenInfo?.partyId ?: run {
        log.warn("PDP response missing partyId for system token")
        return null
    }
    return AuthorizationParty(id = partyId, type = PartyType.System)
        .also { log.info("Authorized party is $it") }
}
