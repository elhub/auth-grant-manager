package no.elhub.auth.v1.features.requests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RequestRepository
import java.util.UUID
import kotlin.time.Instant

class GetRequestHandlerTest : FunSpec({
    val requestRepository = mockk<RequestRepository>()
    val handler = GetRequestHandler(requestRepository)
    val requestedBy = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
    val requestedPrincipal = AuthorizationAgent("999888771", AgentType.OrganizationEntity)
    val requestedOf = AuthorizationAgent("person-1", AgentType.Person)
    val requestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val timestamp = Instant.parse("2026-10-01T08:00:00Z")
    val request = AuthorizationRequest(
        id = requestId,
        requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
        status = AuthorizationRequestStatus.Pending,
        requestedScopes = emptyList(),
        externalReference = null,
        redirectUri = null,
        meta = JsonObject(emptyMap()),
        validFrom = timestamp,
        validTo = Instant.parse("2026-11-01T08:00:00Z"),
        createdAt = timestamp,
        updatedAt = timestamp,
        requestedBy = requestedBy,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        approvedBy = null,
        authorizationGrants = null,
    )

    beforeTest { clearMocks(requestRepository) }

    listOf(requestedBy, requestedOf).forEach { agent ->
        test("returns the request for ${agent.id}") {
            coEvery { requestRepository.find(requestId) } returns request

            handler(requestId, agent) shouldBe request
        }
    }

    listOf(
        requestedPrincipal,
        AuthorizationAgent("unrelated-person", AgentType.Person),
        requestedBy.copy(type = AgentType.Person),
    ).forEach { agent ->
        test("denies access for ${agent.type} ${agent.id}") {
            coEvery { requestRepository.find(requestId) } returns request

            shouldThrow<GetRequestError.NotAuthorized> { handler(requestId, agent) }
        }
    }

    listOf(
        RepositoryReadError.NotFoundError("Missing request"),
        RepositoryReadError.UnexpectedError("Database unavailable"),
    ).forEach { error ->
        test("propagates ${error.javaClass.simpleName}") {
            coEvery { requestRepository.find(requestId) } throws error

            shouldThrow<RepositoryReadError> { handler(requestId, requestedBy) } shouldBeSameInstanceAs error
        }
    }
})
