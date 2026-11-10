package no.elhub.auth.v1.features.grants

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.GrantAgentFilters
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import java.util.UUID
import kotlin.time.Instant

class ListGrantsHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = ListGrantsHandler(grantRepository)
    val authorizedAgent = AuthorizationAgent("7070575000001", AgentType.OrganizationEntity)
    val grantedBy = AuthorizationAgent(UUID.randomUUID().toString(), AgentType.Person)
    val principal = AuthorizationAgent("999888771", AgentType.Organization)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = principal,
        grantedBy = grantedBy,
        grantedTo = authorizedAgent,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val pagination = Pagination(page = 1, size = 2)

    beforeTest { clearMocks(grantRepository) }

    test("forwards the authorized agent, pagination, statuses and agent filters and returns the repository page") {
        val statuses = listOf(AuthorizationGrantStatus.Pending, AuthorizationGrantStatus.Revoked)
        val agentFilters = GrantAgentFilters(
            grantedBy = listOf(grantedBy, AuthorizationAgent(UUID.randomUUID().toString(), AgentType.Person)),
            principal = listOf(principal),
            approvedBy = listOf(grantedBy),
            grantedTo = listOf(authorizedAgent),
        )
        val page = Page(listOf(grant), 3, pagination)
        coEvery { grantRepository.findAll(authorizedAgent, pagination, statuses, agentFilters) } returns page

        handler(authorizedAgent, pagination, statuses, agentFilters) shouldBeSameInstanceAs page
        coVerify(exactly = 1) { grantRepository.findAll(authorizedAgent, pagination, statuses, agentFilters) }
    }

    test("returns an empty page when the agent has no grants") {
        val page = Page(emptyList<AuthorizationGrant>(), 0, pagination)
        coEvery { grantRepository.findAll(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) } returns page

        handler(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) shouldBe page
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { grantRepository.findAll(authorizedAgent, pagination, emptyList(), GrantAgentFilters()) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> {
            handler(authorizedAgent, pagination, emptyList(), GrantAgentFilters())
        } shouldBeSameInstanceAs error
    }
})
