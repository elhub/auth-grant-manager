package no.elhub.auth.v1.features.grants

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import no.elhub.auth.v1.repositories.RepositoryWriteError
import java.util.UUID
import kotlin.time.Instant

class UpdateGrantHandlerTest : FunSpec({
    val grantRepository = mockk<GrantRepository>()
    val handler = UpdateGrantHandler(grantRepository)
    val system = AuthorizationParty("system-1", PartyType.System)
    val principal = AuthorizationParty("999888777", PartyType.Organization)
    val grantedBy = AuthorizationParty("person-1", PartyType.Person)
    val grantedTo = AuthorizationParty("123456789", PartyType.OrganizationEntity)
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
            val updated = grant.copy(type = type, status = status, updatedAt = Instant.parse("2026-10-27T12:00:00Z"))
            coEvery { grantRepository.update(id, status) } returns updated

            handler(id, system, status) shouldBeSameInstanceAs updated

            coVerify(exactly = 1) { grantRepository.update(id, status) }
            coVerify(exactly = 0) { grantRepository.find(any()) }
        }
    }

    listOf(principal, grantedBy, grantedTo).forEach { party ->
        targetStatuses.forEach { status ->
            test("denies ${party.type} updating a grant to $status") {
                shouldThrow<UpdateGrantError.NotAuthorized> { handler(id, party, status) }

                coVerify(exactly = 0) { grantRepository.update(any(), any()) }
            }
        }
    }

    listOf(AuthorizationGrantStatus.Active, AuthorizationGrantStatus.Expired).forEach { status ->
        test("rejects a transition to $status without writing") {
            shouldThrow<UpdateGrantError.InvalidTransition> { handler(id, system, status) }

            coVerify(exactly = 0) { grantRepository.update(any(), any()) }
        }
    }

    listOf(
        RepositoryWriteError.ConflictError("Invalid transition") to UpdateGrantError.InvalidTransition::class,
        RepositoryWriteError.ExpiredError("Grant expired") to UpdateGrantError.Expired::class,
    ).forEach { (repositoryError, expectedError) ->
        targetStatuses.forEach { status ->
            test("maps ${repositoryError::class.simpleName} to ${expectedError.simpleName} when updating to $status") {
                coEvery { grantRepository.update(id, status) } throws repositoryError

                shouldThrow<UpdateGrantError> { handler(id, system, status) }::class shouldBe expectedError
            }
        }
    }

    listOf(
        RepositoryWriteError.NotFoundError("Missing grant"),
        RepositoryWriteError.UnexpectedError("Database unavailable"),
    ).forEach { error ->
        targetStatuses.forEach { status ->
            test("propagates ${error::class.simpleName} when updating to $status") {
                coEvery { grantRepository.update(id, status) } throws error

                shouldThrow<RepositoryWriteError> { handler(id, system, status) } shouldBeSameInstanceAs error
            }
        }
    }
})
