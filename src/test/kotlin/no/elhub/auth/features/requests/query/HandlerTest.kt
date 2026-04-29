package no.elhub.auth.features.requests.query

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class HandlerTest : FunSpec({

    val authorizedParty = AuthorizationParty(id = "org-entity-1", type = PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty(id = "person-1", type = PartyType.Person)
    val requestedToParty = AuthorizationParty(id = "person-2", type = PartyType.Person)

    fun makeRequest(
        id: UUID = UUID.randomUUID(),
        approvedBy: AuthorizationParty? = null,
        grantId: UUID? = null,
    ) = AuthorizationRequest(
        id = id,
        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
        status = AuthorizationRequest.Status.Pending,
        validTo = currentTimeUtc().plusDays(1),
        createdAt = currentTimeUtc(),
        updatedAt = currentTimeUtc(),
        requestedBy = authorizedParty,
        requestedTo = requestedToParty,
        requestedFrom = requestedFromParty,
        approvedBy = approvedBy,
        grantId = grantId,
        properties = emptyList()
    )

    fun requestRepoReturning(page: Page<AuthorizationRequest>): RequestRepository =
        mockk<RequestRepository> {
            coEvery { findAllAndSortByCreatedAt(any(), any(), any()) } returns page.right()
        }

    fun grantRepoReturning(result: Map<UUID, AuthorizationGrant>): GrantRepository =
        mockk<GrantRepository> {
            coEvery { findBySourceIds(any(), any()) } returns result.right()
        }

    test("passes pagination to repository and preserves page metadata") {
        val pagination = Pagination(page = 1, size = 5)
        val request = makeRequest()
        val requestRepo = requestRepoReturning(Page(listOf(request), 10L, pagination))
        val handler = Handler(requestRepo, grantRepoReturning(emptyMap()))

        val response = handler(Query(authorizedParty = authorizedParty, pagination = pagination))

        coVerify(exactly = 1) { requestRepo.findAllAndSortByCreatedAt(authorizedParty, pagination, null) }
        val page = response.shouldBeRight()
        page.pagination shouldBe pagination
        page.totalItems shouldBe 10L
        page.totalPages shouldBe 2
    }

    test("resolves grant id for approved request") {
        val pagination = Pagination()
        val requestId = UUID.randomUUID()
        val approvedRequest = makeRequest(id = requestId, approvedBy = requestedToParty)
        val grant = AuthorizationGrant.create(
            grantedFor = requestedFromParty,
            grantedBy = requestedToParty,
            grantedTo = authorizedParty,
            sourceType = AuthorizationGrant.SourceType.Request,
            sourceId = requestId,
            scopeIds = listOf(UUID.randomUUID()),
            validFrom = currentTimeUtc(),
            validTo = currentTimeUtc().plusDays(365),
        )
        val requestRepo = requestRepoReturning(Page(listOf(approvedRequest), 1L, pagination))
        val handler = Handler(requestRepo, grantRepoReturning(mapOf(requestId to grant)))

        val response = handler(Query(authorizedParty = authorizedParty, pagination = pagination))

        val page = response.shouldBeRight()
        page.items[0].grantId shouldBe grant.id
    }

    test("leaves grant id null for unapproved request") {
        val pagination = Pagination()
        val unapprovedRequest = makeRequest(approvedBy = null)
        val requestRepo = requestRepoReturning(Page(listOf(unapprovedRequest), 1L, pagination))
        val handler = Handler(requestRepo, grantRepoReturning(emptyMap()))

        val response = handler(Query(authorizedParty = authorizedParty, pagination = pagination))

        val page = response.shouldBeRight()
        page.items[0].grantId shouldBe null
    }
})
