package no.elhub.auth.v1.features.grants

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RepositoryWriteError
import java.util.UUID
import kotlin.time.Instant

class UpdateGrantHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = UpdateGrantHandler(grantRepository)
    val system = AuthorizationAgent("system-1", AgentType.System)
    val principal = AuthorizationAgent("999888771", AgentType.Organization)
    val grantedBy = AuthorizationAgent("person-1", AgentType.Person)
    val grantedTo = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
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
    val id = grant.id
    val targetStatuses = listOf(AuthorizationGrantStatus.Revoked, AuthorizationGrantStatus.Exhausted)

    beforeTest { clearMocks(grantRepository) }

    listOf(
        AuthorizationGrantType.Consumable to AuthorizationGrantStatus.Revoked,
        AuthorizationGrantType.Persistent to AuthorizationGrantStatus.Revoked,
        AuthorizationGrantType.Consumable to AuthorizationGrantStatus.Exhausted,
    ).forEach { (type, status) ->
        test("a system can update a $type grant to $status") {
            val existing = grant.copy(type = type)
            val updated = existing.copy(status = status, updatedAt = Instant.parse("2026-10-27T12:00:00Z"))
            coEvery { grantRepository.find(id) } returns existing
            coEvery { grantRepository.update(id, status) } returns updated

            handler(id, system, status, type) shouldBeSameInstanceAs updated

            coVerify(exactly = 1) { grantRepository.update(id, status) }
            coVerify(exactly = 1) { grantRepository.find(id) }
        }
    }

    listOf(principal, grantedBy).forEach { agent ->
        listOf(AuthorizationGrantType.Consumable, AuthorizationGrantType.Persistent).forEach { type ->
            test("${agent.type} ${agent.id} can revoke a $type grant") {
                val existing = grant.copy(type = type)
                val updated = existing.copy(status = AuthorizationGrantStatus.Revoked)
                coEvery { grantRepository.find(id) } returns existing
                coEvery { grantRepository.update(id, AuthorizationGrantStatus.Revoked) } returns updated

                handler(id, agent, AuthorizationGrantStatus.Revoked, type) shouldBeSameInstanceAs updated

                coVerify(exactly = 1) { grantRepository.update(id, AuthorizationGrantStatus.Revoked) }
            }
        }
    }

    listOf(system, principal, grantedBy).forEach { agent ->
        listOf(AuthorizationGrantType.Consumable, AuthorizationGrantType.Persistent).forEach { type ->
            test("${agent.type} ${agent.id} can revoke a pending $type grant") {
                val existing = grant.copy(
                    type = type,
                    status = AuthorizationGrantStatus.Pending,
                    validFrom = Instant.parse("2099-01-01T00:00:00Z"),
                )
                val updated = existing.copy(status = AuthorizationGrantStatus.Revoked)
                coEvery { grantRepository.find(id) } returns existing
                coEvery { grantRepository.update(id, AuthorizationGrantStatus.Revoked) } returns updated

                handler(id, agent, AuthorizationGrantStatus.Revoked, type) shouldBeSameInstanceAs updated

                coVerify(exactly = 1) { grantRepository.update(id, AuthorizationGrantStatus.Revoked) }
            }
        }
    }

    test("maps a repository conflict when exhausting a pending grant to InvalidTransition") {
        coEvery { grantRepository.find(id) } returns grant.copy(
            status = AuthorizationGrantStatus.Pending,
            validFrom = Instant.parse("2099-01-01T00:00:00Z"),
        )
        coEvery { grantRepository.update(id, AuthorizationGrantStatus.Exhausted) } throws
            RepositoryWriteError.ConflictError("Cannot exhaust a pending grant")

        shouldThrow<UpdateGrantError.InvalidTransition> {
            handler(id, system, AuthorizationGrantStatus.Exhausted, AuthorizationGrantType.Consumable)
        }

        coVerify(exactly = 1) { grantRepository.update(id, AuthorizationGrantStatus.Exhausted) }
    }

    listOf(
        AuthorizationGrantType.Persistent to AuthorizationGrantType.Consumable,
        AuthorizationGrantType.Consumable to AuthorizationGrantType.Persistent,
    ).forEach { (suppliedType, storedType) ->
        listOf(system, principal, grantedBy).forEach { agent ->
            test("rejects $suppliedType supplied for a stored $storedType grant by ${agent.type} ${agent.id}") {
                coEvery { grantRepository.find(id) } returns grant.copy(type = storedType)

                shouldThrow<UpdateGrantError.TypeMismatch> {
                    val status = if (agent == system && storedType == AuthorizationGrantType.Consumable) {
                        AuthorizationGrantStatus.Exhausted
                    } else {
                        AuthorizationGrantStatus.Revoked
                    }
                    handler(id, agent, status, suppliedType)
                }

                coVerify(exactly = 1) { grantRepository.find(id) }
                coVerify(exactly = 0) { grantRepository.update(any(), any()) }
            }
        }
    }

    listOf(
        grantedTo,
        AuthorizationAgent("unrelated-person", AgentType.Person),
        AuthorizationAgent("unrelated-org", AgentType.Organization),
        principal.copy(type = AgentType.OrganizationEntity),
        grantedBy.copy(type = AgentType.Organization),
    ).forEach { agent ->
        test("denies ${agent.type} ${agent.id} revoking a grant") {
            coEvery { grantRepository.find(id) } returns grant
            coEvery { grantRepository.update(any(), any()) } throws RepositoryWriteError.ConflictError("Invalid transition")

            shouldThrow<UpdateGrantError.NotAuthorized> {
                handler(id, agent, AuthorizationGrantStatus.Revoked, AuthorizationGrantType.Persistent)
            }

            coVerify(exactly = 0) { grantRepository.update(any(), any()) }
        }
    }

    listOf(principal, grantedBy, grantedTo).forEach { agent ->
        AuthorizationGrantStatus.entries.filter { it != AuthorizationGrantStatus.Revoked }.forEach { status ->
            test("denies ${agent.type} updating a grant to $status before validating the transition") {
                shouldThrow<UpdateGrantError.NotAuthorized> {
                    handler(id, agent, status, AuthorizationGrantType.Persistent)
                }

                coVerify(exactly = 0) { grantRepository.update(any(), any()) }
                coVerify(exactly = 0) { grantRepository.find(any()) }
            }
        }
    }

    listOf(
        RepositoryReadError.NotFoundError("Missing grant"),
        RepositoryReadError.UnexpectedError("Database unavailable"),
    ).forEach { error ->
        listOf(system, principal).forEach { agent ->
            test("propagates ${error::class.simpleName} when loading a grant for revocation by ${agent.type}") {
                coEvery { grantRepository.find(id) } throws error

                shouldThrow<RepositoryReadError> {
                    handler(id, agent, AuthorizationGrantStatus.Revoked, AuthorizationGrantType.Consumable)
                } shouldBeSameInstanceAs error

                coVerify(exactly = 0) { grantRepository.update(any(), any()) }
            }
        }
    }

    listOf(AuthorizationGrantStatus.Pending, AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Expired).forEach { status ->
        test("rejects a transition to $status without writing") {
            coEvery { grantRepository.find(id) } returns grant

            shouldThrow<UpdateGrantError.InvalidTransition> {
                handler(id, system, status, AuthorizationGrantType.Consumable)
            }

            coVerify(exactly = 0) { grantRepository.update(any(), any()) }
            coVerify(exactly = 1) { grantRepository.find(id) }
        }
    }

    listOf(
        RepositoryWriteError.ConflictError("Invalid transition") to UpdateGrantError.InvalidTransition::class,
        RepositoryWriteError.ExpiredError("Grant expired") to UpdateGrantError.Expired::class,
    ).forEach { (repositoryError, expectedError) ->
        targetStatuses.forEach { status ->
            test("maps ${repositoryError::class.simpleName} to ${expectedError.simpleName} when updating to $status") {
                coEvery { grantRepository.find(id) } returns grant
                coEvery { grantRepository.update(id, status) } throws repositoryError

                shouldThrow<UpdateGrantError> {
                    handler(id, system, status, AuthorizationGrantType.Consumable)
                }::class shouldBe expectedError
            }
        }
    }

    listOf(
        RepositoryWriteError.NotFoundError("Missing grant"),
        RepositoryWriteError.UnexpectedError("Database unavailable"),
    ).forEach { error ->
        targetStatuses.forEach { status ->
            test("propagates ${error::class.simpleName} when updating to $status") {
                coEvery { grantRepository.find(id) } returns grant
                coEvery { grantRepository.update(id, status) } throws error

                shouldThrow<RepositoryWriteError> {
                    handler(id, system, status, AuthorizationGrantType.Consumable)
                } shouldBeSameInstanceAs error
            }
        }
    }
})
