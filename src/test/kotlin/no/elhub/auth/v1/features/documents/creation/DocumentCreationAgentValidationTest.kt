package no.elhub.auth.v1.features.documents.creation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationDocumentType

class DocumentCreationAgentValidationTest : FunSpec({
    val requestedBy = AuthorizationAgent("supplier-1", AgentType.OrganizationEntity)
    val requestedPrincipal = AuthorizationAgent("org-1", AgentType.Organization)
    val requestedOf = AuthorizationAgent("org-2", AgentType.Organization)

    AuthorizationDocumentType.entries.forEach { documentType ->
        test("$documentType returns no error for allowed agents") {
            validateDocumentCreationAgents(documentType, requestedBy, requestedPrincipal, requestedOf)
        }

        mapOf(
            "requestedBy" to requestedBy,
            "requestedPrincipal" to requestedPrincipal,
            "requestedOf" to requestedOf,
        ).forEach { (role, validAgent) ->
            AgentType.entries.filter { it != validAgent.type }.forEach { actualType ->
                test("$documentType throws structured errors for $actualType as $role") {
                    val by = if (role == "requestedBy") requestedBy.copy(type = actualType) else requestedBy
                    val principal = if (role == "requestedPrincipal") requestedPrincipal.copy(type = actualType) else requestedPrincipal
                    val of = if (role == "requestedOf") requestedOf.copy(type = actualType) else requestedOf

                    val documentError = shouldThrow<DocumentCreationAgentError> {
                        validateDocumentCreationAgents(documentType, by, principal, of)
                    }
                    when (role) {
                        "requestedBy" -> documentError.shouldBeInstanceOf<DocumentCreationAgentError.RequestedByNotAllowed>()
                        "requestedOf" -> documentError.shouldBeInstanceOf<DocumentCreationAgentError.RequestedOfNotAllowed>()
                        "requestedPrincipal" -> documentError.shouldBeInstanceOf<DocumentCreationAgentError.RequestedPrincipalNotAllowed>()
                    }
                    documentError.documentType shouldBe documentType
                    documentError.actualAgentType shouldBe actualType
                    documentError.expectedAgentType shouldBe validAgent.type
                    documentError.detail shouldBe null
                }
            }
        }
    }
})
