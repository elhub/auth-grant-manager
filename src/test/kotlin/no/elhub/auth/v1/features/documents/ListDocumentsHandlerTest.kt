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
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
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

class ListDocumentsHandlerTest : FunSpec({
    val documentRepository = mockk<DocumentRepository>()
    val grantRepository = mockk<GrantRepository>()
    val handler = ListDocumentsHandler(documentRepository, grantRepository)
    val requestedBy = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
    val document = AuthorizationDocument.new(
        documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = requestedBy,
        requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity),
        requestedOf = requestedOf,
        pdfBytes = byteArrayOf(),
    )
    val documentId = UUID.fromString(document.id)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = documentId,
        principal = document.requestedPrincipal,
        grantedBy = requestedOf,
        grantedTo = requestedBy,
        approvedBy = requestedOf,
        validFrom = document.validFrom,
        validTo = document.validTo,
        scopes = emptyList(),
    )
    val pagination = Pagination(page = 1, size = 2)
    val statuses = listOf(AuthorizationDocumentStatus.Approved, AuthorizationDocumentStatus.Expired)

    beforeTest { clearMocks(documentRepository, grantRepository) }

    listOf(requestedBy, requestedOf).forEach { party ->
        test("passes party, pagination and statuses to the repository for ${party.id}") {
            val page = Page(listOf(document), 5, pagination)
            coEvery { documentRepository.findAndSortByCreatedAt(party, pagination, statuses) } returns page
            coEvery {
                grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(documentId))
            } returns mapOf(documentId to listOf(grant))

            handler(party, pagination, statuses) shouldBe page.copy(
                items = listOf(document.copy(authorizationGrants = listOf(grant))),
            )
        }
    }

    test("batch loads all grants and preserves document ordering and pagination") {
        val secondId = UUID.randomUUID()
        val second = document.copy(id = secondId.toString())
        val revokedGrant = grant.copy(id = UUID.randomUUID(), status = AuthorizationGrantStatus.Revoked)
        val secondGrant = grant.copy(id = UUID.randomUUID(), sourceId = secondId)
        val page = Page(listOf(document, second), 5, pagination)
        coEvery { documentRepository.findAndSortByCreatedAt(requestedBy, pagination, statuses) } returns page
        coEvery {
            grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(documentId, secondId))
        } returns mapOf(secondId to listOf(secondGrant), documentId to listOf(grant, revokedGrant))

        handler(requestedBy, pagination, statuses) shouldBe page.copy(
            items = listOf(
                document.copy(authorizationGrants = listOf(grant, revokedGrant)),
                second.copy(authorizationGrants = listOf(secondGrant)),
            ),
        )
        coVerify(exactly = 1) { grantRepository.findBySourceIds(any(), any()) }
    }

    test("sets an empty grant relationship when no grants match") {
        coEvery { documentRepository.findAndSortByCreatedAt(requestedBy, pagination, emptyList()) } returns
            Page(listOf(document), 1, pagination)
        coEvery { grantRepository.findBySourceIds(any(), any()) } returns emptyMap()

        handler(requestedBy, pagination, emptyList()).items.single().authorizationGrants shouldBe emptyList()
    }

    test("returns an empty page without looking up grants") {
        val page = Page<AuthorizationDocument>(emptyList(), 5, pagination)
        coEvery { documentRepository.findAndSortByCreatedAt(requestedBy, pagination, statuses) } returns page

        handler(requestedBy, pagination, statuses) shouldBeSameInstanceAs page
        coVerify(exactly = 0) { grantRepository.findBySourceIds(any(), any()) }
    }

    test("propagates a document repository failure without looking up grants") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { documentRepository.findAndSortByCreatedAt(requestedBy, pagination, statuses) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> {
            handler(requestedBy, pagination, statuses)
        } shouldBeSameInstanceAs error
        coVerify(exactly = 0) { grantRepository.findBySourceIds(any(), any()) }
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { documentRepository.findAndSortByCreatedAt(requestedBy, pagination, statuses) } returns
            Page(listOf(document), 1, pagination)
        coEvery { grantRepository.findBySourceIds(any(), any()) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> {
            handler(requestedBy, pagination, statuses)
        } shouldBeSameInstanceAs error
    }
})
