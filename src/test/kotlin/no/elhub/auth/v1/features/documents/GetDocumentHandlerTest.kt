package no.elhub.auth.v1.features.documents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import java.util.UUID
import kotlin.time.Instant

class GetDocumentHandlerTest : FunSpec({
    val documentRepository = mockk<DocumentRepository>()
    val grantRepository = mockk<GrantRepository>()
    val handler = GetDocumentHandler(documentRepository, grantRepository)
    val requestedBy = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val document = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = "contract-123",
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        pdfBytes = "%PDF-1.7\nexample document".toByteArray(),
    )
    val documentId = UUID.fromString(document.id)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = documentId,
        principal = requestedPrincipal,
        grantedBy = requestedOf,
        grantedTo = requestedBy,
        approvedBy = requestedOf,
        validFrom = document.validFrom,
        validTo = document.validTo,
        scopes = emptyList(),
    )

    beforeTest { clearMocks(documentRepository, grantRepository) }

    listOf(requestedBy, requestedOf).forEach { party ->
        test("returns the document with linked grants for ${party.id}") {
            coEvery { documentRepository.find(documentId) } returns document
            coEvery {
                grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(documentId))
            } returns mapOf(documentId to listOf(grant))

            handler(documentId, party) shouldBe document.copy(authorizationGrant = listOf(grant))
        }
    }

    test("returns all corresponding grants regardless of their status") {
        val revokedGrant = grant.copy(id = UUID.randomUUID(), status = AuthorizationGrantStatus.Revoked)
        val grants = listOf(grant, revokedGrant)
        coEvery { documentRepository.find(documentId) } returns document
        coEvery {
            grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(documentId))
        } returns mapOf(documentId to grants)

        handler(documentId, requestedBy) shouldBe document.copy(authorizationGrant = grants)
    }

    test("returns an empty grant relationship when no corresponding grants exist") {
        coEvery { documentRepository.find(documentId) } returns document
        coEvery {
            grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(documentId))
        } returns emptyMap()

        handler(documentId, requestedBy) shouldBe document.copy(authorizationGrant = emptyList())
    }

    listOf(
        requestedPrincipal,
        AuthorizationParty("unrelated-person", PartyType.Person),
        requestedBy.copy(type = PartyType.Person),
    ).forEach { party ->
        test("denies access for ${party.type} ${party.id} before looking up grants") {
            coEvery { documentRepository.find(documentId) } returns document

            shouldThrow<GetDocumentError.NotAuthorized> { handler(documentId, party) }
            coVerify(exactly = 0) { grantRepository.findBySourceIds(any(), any()) }
        }
    }

    test("propagates not found when the document does not exist") {
        val error = RepositoryReadError.NotFoundError("Missing document")
        coEvery { documentRepository.find(documentId) } throws error

        shouldThrow<RepositoryReadError.NotFoundError> { handler(documentId, requestedBy) } shouldBeSameInstanceAs error
        coVerify(exactly = 0) { grantRepository.findBySourceIds(any(), any()) }
    }

    test("propagates a document repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { documentRepository.find(documentId) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> { handler(documentId, requestedBy) } shouldBeSameInstanceAs error
        coVerify(exactly = 0) { grantRepository.findBySourceIds(any(), any()) }
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { documentRepository.find(documentId) } returns document
        coEvery {
            grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(documentId))
        } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> { handler(documentId, requestedBy) } shouldBeSameInstanceAs error
    }
})
