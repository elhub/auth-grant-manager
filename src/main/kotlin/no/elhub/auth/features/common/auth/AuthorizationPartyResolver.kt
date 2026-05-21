package no.elhub.auth.features.common.auth

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import org.slf4j.LoggerFactory

const val AUTHINFO_POLICY_ROUTE = "/v1/data/v3/token/authinfo"

private val log = LoggerFactory.getLogger("AuthorizationPartyResolver")

fun resolveAuthorizedParty(response: AuthInfoPolicy.Response): AuthorizationParty? = when (response.tokenInfo?.tokenType) {
    TokenType.MASKINPORTEN -> resolveMaskinportenParty(response)

    TokenType.ENDUSER -> resolveEndUserParty(response)

    TokenType.ELHUB_SERVICE -> resolveSystemParty(response)

    else -> {
        log.warn("Unexpected or missing tokenType={}", response.tokenInfo?.tokenType)
        null
    }
}

private fun resolveMaskinportenParty(response: AuthInfoPolicy.Response): AuthorizationParty? {
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

private fun resolveEndUserParty(response: AuthInfoPolicy.Response): AuthorizationParty? {
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

private fun resolveSystemParty(response: AuthInfoPolicy.Response): AuthorizationParty? {
    val partyId = response.tokenInfo?.partyId ?: run {
        log.warn("PDP response missing partyId for system token")
        return null
    }
    return AuthorizationParty(id = partyId, type = PartyType.System)
        .also { log.info("Authorized party is $it") }
}
