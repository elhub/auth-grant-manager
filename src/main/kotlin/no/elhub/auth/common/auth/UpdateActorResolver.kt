package no.elhub.auth.common.auth

import no.elhub.auth.plugin.dto.TokenType
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.policies.token.core.TokenPolicy
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("UpdateActorResolver")

/**
 * Same PDP authinfo route as [AuthInfoPolicy], registered under its own key so the
 * update routes can resolve the person rather than the organisation they represent.
 */
object UpdateAuthInfoPolicy : TokenPolicy<AuthInfoPolicy.Request, AuthInfoPolicy.Response>(
    key = "token.authinfo.update",
    route = AUTHINFO_POLICY_ROUTE,
) {
    override val requestSerializer = AuthInfoPolicy.Request.serializer()
    override val responsePayloadSerializer = AuthInfoPolicy.Response.serializer()
}

/** Grant updates additionally allow systems, which are the only actors permitted to exhaust grants. */
object GrantUpdateAuthInfoPolicy : TokenPolicy<AuthInfoPolicy.Request, AuthInfoPolicy.Response>(
    key = "token.authinfo.update-authorization-grant",
    route = AUTHINFO_POLICY_ROUTE,
) {
    override val requestSerializer = AuthInfoPolicy.Request.serializer()
    override val responsePayloadSerializer = AuthInfoPolicy.Response.serializer()
}

/**
 * Resolves the person performing an update, or null if not allowed. Systems must be explicitly enabled.
 * When acting on behalf of an organisation, the PDP verifies the delegation; the person is still returned.
 */
fun resolveUpdateActor(response: AuthInfoPolicy.Response, allowSystem: Boolean = false): AuthorizationParty? {
    val tokenType = response.tokenInfo?.tokenType
    if (allowSystem && tokenType == TokenType.ELHUB_SERVICE) {
        return resolveAuthorizedParty(response)
    }
    if (tokenType != TokenType.ENDUSER) {
        log.warn("Unsupported tokenType={} for update", tokenType)
        return null
    }

    val authInfo = response.authInfo
    if (authInfo == null) {
        log.warn("PDP response missing authInfo")
        return null
    }
    if (authInfo.error != null) {
        log.warn("PDP authInfo error={}", authInfo.error)
        return null
    }

    val personId = when (val actingType = authInfo.actingType?.trim()?.lowercase()) {
        "person" -> authInfo.actingId?.ifBlank { null } ?: run {
            log.warn("PDP response missing actingId for person")
            return null
        }

        "organisation" -> authInfo.originalId?.ifBlank { null } ?: run {
            log.warn("PDP response missing originalId for person acting on behalf of organisation")
            return null
        }

        else -> {
            log.warn("Unsupported actingType={} for update", actingType)
            return null
        }
    }

    return AuthorizationParty(id = personId, type = PartyType.Person)
        .also { log.info("Authorized party is $it") }
}
