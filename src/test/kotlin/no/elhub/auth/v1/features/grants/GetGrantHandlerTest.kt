package no.elhub.auth.v1.features.grants

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import java.util.UUID
import kotlin.time.Instant

class GetGrantHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = GetGrantHandler(grantRepository)
    val principal = AuthorizationAgent("999888771", AgentType.OrganizationEntity)
    val grantedBy = AuthorizationAgent("person-1", AgentType.Person)
    val grantedTo = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
    val system = AuthorizationAgent("system-1", AgentType.System)
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Document,
        sourceId = UUID.randomUUID(),
        principal = principal,
        grantedBy = grantedBy,
        grantedTo = grantedTo,
        approvedBy = grantedBy,
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    val grantId = grant.id

    beforeTest { clearMocks(grantRepository) }

    listOf(grantedTo, principal, system).forEach { agent ->
        test("returns the grant for ${agent.type} ${agent.id}") {
            coEvery { grantRepository.find(grantId) } returns grant

            handler(grantId, agent) shouldBe grant
        }
    }

    listOf(
        grantedBy,
        AuthorizationAgent("unrelated-org", AgentType.OrganizationEntity),
        grantedTo.copy(type = AgentType.Person),
    ).forEach { agent ->
        test("denies access for ${agent.type} ${agent.id}") {
            coEvery { grantRepository.find(grantId) } returns grant

            shouldThrow<GetGrantError.NotAuthorized> { handler(grantId, agent) }
        }
    }

    test("propagates not found when the grant does not exist") {
        val error = RepositoryReadError.NotFoundError("Missing grant")
        coEvery { grantRepository.find(grantId) } throws error

        shouldThrow<RepositoryReadError.NotFoundError> { handler(grantId, grantedTo) } shouldBeSameInstanceAs error
    }

    test("propagates a grant repository failure") {
        val error = RepositoryReadError.UnexpectedError("Database unavailable")
        coEvery { grantRepository.find(grantId) } throws error

        shouldThrow<RepositoryReadError.UnexpectedError> { handler(grantId, grantedTo) } shouldBeSameInstanceAs error
    }
})
