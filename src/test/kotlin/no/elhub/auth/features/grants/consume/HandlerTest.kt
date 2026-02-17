package no.elhub.auth.features.grants.consume

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.elhub.auth.features.common.AuthorizationParties
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.common.GrantRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class HandlerTest : FunSpec({

    val grantId = UUID.randomUUID()
    val newStatus = Status.Exhausted
    val grantedFor = AuthorizationParty(id = "person-1", type = PartyType.Person)
    val grantedBy = AuthorizationParty(id = "issuer-1", type = PartyType.Organization)
    val grantedTo = AuthorizationParty(id = "org-entity-1", type = PartyType.OrganizationEntity)
    val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())

    val updatedGrant = AuthorizationGrant(
        id = grantId,
        grantStatus = newStatus,
        grantedFor = grantedFor,
        grantedBy = grantedBy,
        grantedTo = grantedTo,
        grantedAt = currentTimeWithTimeZone(),
        validFrom = currentTimeWithTimeZone(),
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone(),
        validTo = currentTimeWithTimeZone().plusYears(1),
        sourceType = SourceType.Document,
        sourceId = UUID.randomUUID(),
        scopeIds = scopeIds
    )
    val activeGrant = AuthorizationGrant(
        id = grantId,
        grantStatus = Status.Active,
        grantedFor = grantedFor,
        grantedBy = grantedBy,
        grantedTo = grantedTo,
        grantedAt = currentTimeWithTimeZone(),
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone(),
        validFrom = currentTimeWithTimeZone().minusDays(1),
        validTo = currentTimeWithTimeZone().plusYears(1),
        sourceType = SourceType.Document,
        sourceId = UUID.randomUUID(),
        scopeIds = scopeIds
    )

    fun repoReturning(
        updateResult: Either<RepositoryError, AuthorizationGrant>,
        findResult: Either<RepositoryReadError, AuthorizationGrant> = activeGrant.right(),
    ): GrantRepository =
        mockk<GrantRepository> {
            every { update(grantId, newStatus) } returns updateResult
            every { find(grantId) } returns findResult
        }

    test("returns NotAuthorized when authorized party is not consent management system") {
        val repo = mockk<GrantRepository>(relaxed = true)
        val handler = Handler(repo)

        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParty(id = "other-system", type = PartyType.System)
            )
        )

        response.shouldBeLeft(ConsumeError.NotAuthorized)
        verify(exactly = 0) { repo.update(any(), any()) }
    }

    test("maps repository error to PersistenceError") {
        val error: RepositoryError = RepositoryWriteError.UnexpectedError
        val handler = Handler(repoReturning(updateResult = error.left()))

        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParties.ConsentManagementSystem
            )
        )

        response.shouldBeLeft(ConsumeError.PersistenceError)
    }

    test("returns updated grant when authorized party is consent management system") {
        val handler = Handler(repoReturning(updateResult = updatedGrant.right()))

        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParties.ConsentManagementSystem
            )
        )

        response.shouldBeRight(updatedGrant)
    }

    test("returns ExpiredError when grant is expired") {
        val expiredGrant = activeGrant.copy(
            validTo = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        )

        val handler = Handler(
            repoReturning(
                findResult = expiredGrant.right(),
                updateResult = updatedGrant.right()
            )
        )
        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParties.ConsentManagementSystem
            )
        )

        response.shouldBeLeft(ConsumeError.ExpiredError)
    }

    test("returns IllegalStateError when grant is not active") {
        val exhaustedGrant = activeGrant.copy(grantStatus = Status.Exhausted)

        val handler = Handler(
            repoReturning(
                findResult = exhaustedGrant.right(),
                updateResult = exhaustedGrant.right()
            )
        )

        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParties.ConsentManagementSystem
            )
        )

        response.shouldBeLeft(ConsumeError.IllegalStateError)
    }

    test("returns IllegalTransitionError when attempting to update grant to 'Active'") {
        val handler = Handler(
            repoReturning(
                findResult = activeGrant.right(),
                updateResult = updatedGrant.right()
            )
        )
        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = Status.Active,
                authorizedParty = AuthorizationParties.ConsentManagementSystem
            )
        )

        response.shouldBeLeft(ConsumeError.IllegalTransitionError)
    }
})
