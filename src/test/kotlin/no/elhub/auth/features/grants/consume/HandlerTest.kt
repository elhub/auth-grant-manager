package no.elhub.auth.features.grants.consume

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
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class HandlerTest : FunSpec({

    val grantId = UUID.randomUUID()
    val newStatus = Status.Exhausted
    val grantedFor = AuthorizationParty(resourceId = "person-1", type = PartyType.Person)
    val grantedBy = AuthorizationParty(resourceId = "issuer-1", type = PartyType.Organization)
    val grantedTo = AuthorizationParty(resourceId = "org-entity-1", type = PartyType.OrganizationEntity)
    val updatedGrant = AuthorizationGrant(
        id = grantId,
        grantStatus = newStatus,
        grantedFor = grantedFor,
        grantedBy = grantedBy,
        grantedTo = grantedTo,
        grantedAt = currentTimeWithTimeZone(),
        validFrom = currentTimeWithTimeZone(),
        validTo = currentTimeWithTimeZone().plusYears(1),
        sourceType = SourceType.Document,
        sourceId = UUID.randomUUID(),
    )

    fun repoReturning(result: arrow.core.Either<RepositoryError, AuthorizationGrant>): GrantRepository =
        mockk<GrantRepository> {
            every { update(grantId, newStatus) } returns result
        }

    test("returns NotAuthorized when authorized party is not consent management system") {
        val repo = mockk<GrantRepository>(relaxed = true)
        val handler = Handler(repo)

        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParty(resourceId = "other-system", type = PartyType.System)
            )
        )

        response.shouldBeLeft(ConsumeError.NotAuthorized)
        verify(exactly = 0) { repo.update(any(), any()) }
    }

    test("maps repository error to PersistenceError") {
        val error: RepositoryError = RepositoryWriteError.UnexpectedError
        val handler = Handler(repoReturning(result = error.left()))

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
        val handler = Handler(repoReturning(result = updatedGrant.right()))

        val response = handler(
            ConsumeCommand(
                grantId = grantId,
                newStatus = newStatus,
                authorizedParty = AuthorizationParties.ConsentManagementSystem
            )
        )

        response.shouldBeRight(updatedGrant)
    }
})
