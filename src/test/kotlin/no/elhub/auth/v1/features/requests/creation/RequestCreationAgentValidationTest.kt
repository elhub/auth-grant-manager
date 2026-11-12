package no.elhub.auth.v1.features.requests.creation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequestType

class RequestCreationAgentValidationTest : FunSpec({
    val requestedBy = AuthorizationAgent("supplier-1", AgentType.OrganizationEntity)
    val requestedPrincipal = AuthorizationAgent("org-1", AgentType.Organization)
    val requestedOf = AuthorizationAgent("org-2", AgentType.Organization)

    AuthorizationRequestType.entries.forEach { requestType ->
        test("$requestType returns no error for allowed agents") {
            validateRequestCreationAgents(requestType, requestedBy, requestedPrincipal, requestedOf)
        }

        mapOf(
            "requestedBy" to requestedBy,
            "requestedPrincipal" to requestedPrincipal,
            "requestedOf" to requestedOf,
        ).forEach { (role, validAgent) ->
            AgentType.entries.filter { it != validAgent.type }.forEach { actualType ->
                test("$requestType throws structured errors for $actualType as $role") {
                    val by = if (role == "requestedBy") requestedBy.copy(type = actualType) else requestedBy
                    val principal = if (role == "requestedPrincipal") requestedPrincipal.copy(type = actualType) else requestedPrincipal
                    val of = if (role == "requestedOf") requestedOf.copy(type = actualType) else requestedOf

                    val requestError = shouldThrow<RequestCreationAgentError> {
                        validateRequestCreationAgents(requestType, by, principal, of)
                    }
                    when (role) {
                        "requestedBy" -> requestError.shouldBeInstanceOf<RequestCreationAgentError.RequestedByNotAllowed>()
                        "requestedOf" -> requestError.shouldBeInstanceOf<RequestCreationAgentError.RequestedOfNotAllowed>()
                        "requestedPrincipal" -> requestError.shouldBeInstanceOf<RequestCreationAgentError.RequestedPrincipalNotAllowed>()
                    }
                    requestError.requestType shouldBe requestType
                    requestError.actualAgentType shouldBe actualType
                    requestError.expectedAgentType shouldBe validAgent.type
                    requestError.detail shouldBe null
                }
            }
        }
    }
})
