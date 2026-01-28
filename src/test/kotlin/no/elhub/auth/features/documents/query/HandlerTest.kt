package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class HandlerTest : FunSpec({

    val requestedBy = AuthorizationParty(resourceId = "org-entity-1", type = PartyType.OrganizationEntity)
    val requestedFrom = AuthorizationParty(resourceId = "person-1", type = PartyType.Person)
    val requestedTo = AuthorizationParty(resourceId = "person-2", type = PartyType.Person)
    val validTo = currentTimeWithTimeZone().plusDays(1)
    val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())

    val firstDocumentId = UUID.randomUUID()
    val secondDocumentId = UUID.randomUUID()

    val firstDocument = AuthorizationDocument(
        id = firstDocumentId,
        type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = "file-1".toByteArray(),
        requestedBy = requestedBy,
        requestedFrom = requestedFrom,
        requestedTo = requestedTo,
        properties = listOf(AuthorizationDocumentProperty(key = "k1", value = "v1")),
        validTo = validTo,
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone()
    )

    val secondDocument = AuthorizationDocument(
        id = secondDocumentId,
        type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = "file-2".toByteArray(),
        requestedBy = requestedBy,
        requestedFrom = requestedFrom,
        requestedTo = requestedTo,
        properties = listOf(AuthorizationDocumentProperty(key = "k2", value = "v2")),
        validTo = validTo,
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone()
    )

    val grant = AuthorizationGrant.create(
        grantedFor = requestedFrom,
        grantedBy = requestedTo,
        grantedTo = requestedBy,
        sourceType = AuthorizationGrant.SourceType.Document,
        sourceId = firstDocumentId,
        scopeIds = scopeIds
    )

    fun documentRepoReturning(result: Either<RepositoryReadError, List<AuthorizationDocument>>): DocumentRepository =
        mockk<DocumentRepository> {
            every { findAll(any()) } returns result
        }

    fun grantRepoReturning(
        firstResult: Either<RepositoryReadError, AuthorizationGrant?>,
        secondResult: Either<RepositoryReadError, AuthorizationGrant?>
    ): GrantRepository =
        mockk<GrantRepository> {
            every {
                findBySource(AuthorizationGrant.SourceType.Document, firstDocumentId)
            } returns firstResult
            every {
                findBySource(AuthorizationGrant.SourceType.Document, secondDocumentId)
            } returns secondResult
        }

    test("returns documents with grant ids resolved for authorized party") {
        val noGrant: Either<RepositoryReadError, AuthorizationGrant?> = null.right()

        val documentRepo = documentRepoReturning(listOf(firstDocument, secondDocument).right())
        val handler = Handler(documentRepo, grantRepoReturning(grant.right(), noGrant))

        val response = handler(
            Query(
                authorizedParty = requestedBy
            )
        )

        verify(exactly = 1) { documentRepo.findAll(requestedBy) }
        response.shouldBeRight(
            listOf(
                firstDocument.copy(grantId = grant.id),
                secondDocument.copy(grantId = null)
            )
        )
    }
})
