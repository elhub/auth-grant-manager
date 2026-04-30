package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.todayOslo
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

//  TODO add test for status param

class HandlerTest : FunSpec({

    val requestedBy = AuthorizationParty(id = "org-entity-1", type = PartyType.OrganizationEntity)
    val requestedFrom = AuthorizationParty(id = "person-1", type = PartyType.Person)
    val requestedTo = AuthorizationParty(id = "person-2", type = PartyType.Person)
    val grantValidFrom = todayOslo()
    val grantValidTo = todayOslo().plus(DatePeriod(years = 1))
    val validTo = currentTimeUtc().plusDays(1)
    val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())

    val firstDocumentId = UUID.randomUUID()
    val secondDocumentId = UUID.randomUUID()

    val firstDocument = AuthorizationDocument(
        id = firstDocumentId,
        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = "file-1".toByteArray(),
        requestedBy = requestedBy,
        requestedFrom = requestedFrom,
        requestedTo = requestedTo,
        properties = listOf(AuthorizationDocumentProperty(key = "k1", value = "v1")),
        validTo = validTo,
        createdAt = currentTimeUtc(),
        updatedAt = currentTimeUtc()
    )

    val secondDocument = AuthorizationDocument(
        id = secondDocumentId,
        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = "file-2".toByteArray(),
        requestedBy = requestedBy,
        requestedFrom = requestedFrom,
        requestedTo = requestedTo,
        properties = listOf(AuthorizationDocumentProperty(key = "k2", value = "v2")),
        validTo = validTo,
        createdAt = currentTimeUtc(),
        updatedAt = currentTimeUtc()
    )

    val grant = AuthorizationGrant.create(
        grantedFor = requestedFrom,
        grantedBy = requestedTo,
        grantedTo = requestedBy,
        sourceType = AuthorizationGrant.SourceType.Document,
        sourceId = firstDocumentId,
        scopeIds = scopeIds,
        validFrom = grantValidFrom.toTimeZoneOffsetDateTimeAtStartOfDay(),
        validTo = grantValidTo.toTimeZoneOffsetDateTimeAtStartOfDay()
    )

    fun documentRepoReturning(result: Either<RepositoryReadError, Page<AuthorizationDocument>>): DocumentRepository =
        mockk<DocumentRepository> {
            coEvery { findAndSortByCreatedAt(any(), any(), any()) } returns result
        }

    fun grantRepoReturning(result: Either<RepositoryReadError, Map<UUID, AuthorizationGrant>>): GrantRepository =
        mockk<GrantRepository> {
            coEvery {
                findBySourceIds(AuthorizationGrant.SourceType.Document, any())
            } returns result
        }

    test("passes pagination from query to repository and preserves page metadata") {
        val pagination = Pagination(page = 1, size = 1)
        val documentRepo = documentRepoReturning(Page(listOf(firstDocument), 2L, pagination).right())
        val handler = Handler(documentRepo, grantRepoReturning(emptyMap<UUID, AuthorizationGrant>().right()))

        val response = handler(
            Query(
                authorizedParty = requestedBy,
                pagination = pagination,
            )
        )

        coVerify(exactly = 1) { documentRepo.findAndSortByCreatedAt(requestedBy, pagination, emptyList()) }
        val page = response.shouldBeRight()
        page.pagination shouldBe pagination
        page.totalItems shouldBe 2L
        page.totalPages shouldBe 2
    }

    test("returns documents with grant ids resolved for authorized party") {
        val pagination = Pagination()
        val documentRepo = documentRepoReturning(Page(listOf(firstDocument, secondDocument), 2L, pagination).right())
        // first document has a grant, second does not
        val grantMap = mapOf(firstDocumentId to grant)
        val handler = Handler(documentRepo, grantRepoReturning(grantMap.right()))

        val response = handler(
            Query(
                authorizedParty = requestedBy,
                pagination = pagination,
            )
        )

        coVerify(exactly = 1) { documentRepo.findAndSortByCreatedAt(requestedBy, pagination, emptyList()) }
        response.shouldBeRight(
            Page(
                items = listOf(
                    firstDocument.copy(grantId = grant.id),
                    secondDocument.copy(grantId = null)
                ),
                totalItems = 2L,
                pagination = pagination,
            )
        )
    }
})
