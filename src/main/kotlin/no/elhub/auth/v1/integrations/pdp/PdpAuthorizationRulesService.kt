package no.elhub.auth.v1.integrations.pdp

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.plugin.policies.token.base.agentrepresentative.AgentRepresentativePolicy
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.common.TraceIdContext
import no.elhub.auth.v1.common.AuthorizationRulesService
import org.slf4j.LoggerFactory

class PdpAuthorizationRulesService(
    private val httpClient: HttpClient,
    private val pdpBaseUrl: String,
    private val tokenProvider: JwtTokenProvider,
) : AuthorizationRulesService {
    private val logger = LoggerFactory.getLogger(PdpAuthorizationRulesService::class.java)

    override suspend fun canActOnBehalfOf(
        representative: AuthorizationParty,
        principal: AuthorizationParty,
    ): Boolean {
        logger.info("Checking authorization: principal={}, requestedOf={}", principal, representative)

        val traceId = checkNotNull(TraceIdContext.currentOrNull()) {
            "Missing trace ID for PDP authorization"
        }
        val serviceToken = tokenProvider.getToken()
        val policy = AgentRepresentativePolicy

        val response = httpClient.post("${pdpBaseUrl.trimEnd('/')}${policy.route}") {
            contentType(ContentType.Application.Json)
            setBody(
                policy.encodeRequest(
                    token = serviceToken,
                    traceId = traceId,
                    payload = AgentRepresentativePolicy.Request(
                        representativeId = representative.id,
                        principalId = principal.id,
                    ),
                ).toString()
            )
        }

        if (!response.status.isSuccess()) {
            throw PdpAuthorizationException("PDP request failed with status ${response.status.value}")
        }

        val result = policy.decodeResponse(response.bodyAsText()).result
        if (result.error != null) {
            throw PdpAuthorizationException("PDP returned an authorization error")
        }
        if (result.tokenInfo?.tokenStatus != "verified") {
            throw PdpAuthorizationException("PDP did not verify the service token")
        }

        return result.authorized
    }
}

class PdpAuthorizationException(message: String) : RuntimeException(message)
