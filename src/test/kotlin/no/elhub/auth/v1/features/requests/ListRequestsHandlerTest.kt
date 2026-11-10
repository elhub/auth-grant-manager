package no.elhub.auth.v1.features.requests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RequestRepository
import kotlin.time.Instant

class ListRequestsHandlerTest : FunSpec({
    val requestRepository = mockk<RequestRepository>()
    val handler = ListRequestsHandler(requestRepository)
    val agent = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
    val pagination = Pagination(page = 1, size = 2)
    val statuses = listOf(AuthorizationRequestStatus.Pending, AuthorizationRequestStatus.Approved)
    val request = AuthorizationRequest.new(
        requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
        requestedScopes = emptyList(),
        externalReference = null,
        meta = JsonObject(emptyMap()),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        requestedBy = agent,
        requestedPrincipal = agent,
        requestedOf = AuthorizationAgent("person-1", AgentType.Person),
    )

    beforeTest { clearMocks(requestRepository) }

    test("returns the repository page for the agent, pagination and statuses") {
        val page = Page(listOf(request), 5, pagination)
        coEvery { requestRepository.findAndSortByCreatedAt(agent, pagination, statuses) } returns page

        handler(agent, pagination, statuses) shouldBe page
    }

    test("propagates a repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { requestRepository.findAndSortByCreatedAt(agent, pagination, statuses) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> { handler(agent, pagination, statuses) } shouldBeSameInstanceAs error
    }
})
