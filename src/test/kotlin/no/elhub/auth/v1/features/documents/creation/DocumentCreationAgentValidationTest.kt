package no.elhub.auth.v1.features.documents.creation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationDocumentType

class DocumentCreationAgentValidationTest : FunSpec({
    val requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("org-1", PartyType.Organization)
    val requestedOf = AuthorizationParty("org-2", PartyType.Organization)

    AuthorizationDocumentType.entries.forEach { documentType ->
        test("$documentType returns no error for allowed agents") {
            validateDocumentCreationAgents(documentType, requestedBy, requestedPrincipal, requestedOf) shouldBe null
        }

        mapOf(
            "requestedBy" to requestedBy,
            "requestedPrincipal" to requestedPrincipal,
            "requestedOf" to requestedOf,
        ).forEach { (role, validParty) ->
            PartyType.entries.filter { it != validParty.type }.forEach { actualType ->
                test("$documentType returns structured errors for $actualType as $role without throwing") {
                    val by = if (role == "requestedBy") requestedBy.copy(type = actualType) else requestedBy
                    val principal = if (role == "requestedPrincipal") requestedPrincipal.copy(type = actualType) else requestedPrincipal
                    val of = if (role == "requestedOf") requestedOf.copy(type = actualType) else requestedOf

                    val documentError = validateDocumentCreationAgents(documentType, by, principal, of)
                        .shouldBeInstanceOf<DocumentCreationAgentError>()
                    when (role) {
                        "requestedBy" -> documentError.shouldBeInstanceOf<DocumentCreationAgentError.RequestedByNotAllowed>()
                        "requestedOf" -> documentError.shouldBeInstanceOf<DocumentCreationAgentError.RequestedOfNotAllowed>()
                        "requestedPrincipal" -> documentError.shouldBeInstanceOf<DocumentCreationAgentError.RequestedPrincipalNotAllowed>()
                    }
                    documentError.documentType shouldBe documentType
                    documentError.actualAgentType shouldBe actualType
                    documentError.expectedAgentType shouldBe validParty.type
                    documentError.detail shouldBe null
                }
            }
        }
    }
})
