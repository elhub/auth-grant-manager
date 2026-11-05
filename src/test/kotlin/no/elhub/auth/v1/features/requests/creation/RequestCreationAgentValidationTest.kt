package no.elhub.auth.v1.features.requests.creation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationRequestType

class RequestCreationAgentValidationTest : FunSpec({
    val requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("org-1", PartyType.Organization)
    val requestedOf = AuthorizationParty("org-2", PartyType.Organization)

    AuthorizationRequestType.entries.forEach { requestType ->
        test("$requestType returns no error for allowed agents") {
            validateRequestCreationAgents(requestType, requestedBy, requestedPrincipal, requestedOf)
        }

        mapOf(
            "requestedBy" to requestedBy,
            "requestedPrincipal" to requestedPrincipal,
            "requestedOf" to requestedOf,
        ).forEach { (role, validParty) ->
            PartyType.entries.filter { it != validParty.type }.forEach { actualType ->
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
                    requestError.expectedAgentType shouldBe validParty.type
                    requestError.detail shouldBe null
                }
            }
        }
    }
})
