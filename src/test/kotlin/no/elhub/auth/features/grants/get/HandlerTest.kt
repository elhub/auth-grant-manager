package no.elhub.auth.features.grants.get

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.elhub.auth.features.common.Constants
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class HandlerTest : FunSpec({

    val authorizedSystem = AuthorizationParty(id = Constants.CONSENT_MANAGEMENT_OSB_ID, type = PartyType.System)
    val unAuthorizedSystem = AuthorizationParty(id = "invalid-system", type = PartyType.System)
    val grantedFor = AuthorizationParty(id = "person-1", type = PartyType.Person)
    val grantedBy = AuthorizationParty(id = "issuer-1", type = PartyType.Organization)
    val grantedTo = AuthorizationParty(id = "org-entity-1", type = PartyType.OrganizationEntity)
    val grantId = UUID.randomUUID()
    val scopeIds = listOf(UUID.randomUUID(), UUID.randomUUID())

    val grant =
        AuthorizationGrant(
            id = grantId,
            grantStatus = Status.Active,
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
            scopeIds = scopeIds,
            properties = emptyList()
        )

    fun repoReturning(result: arrow.core.Either<RepositoryReadError, AuthorizationGrant>): GrantRepository =
        mockk<GrantRepository> {
            every { find(grantId) } returns result
        }

    test("returns grant when authorized party is valid System") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = authorizedSystem,
            )
        )

        response.shouldBeRight(grant)
    }

    test("returns NotAuthorized when authorized party is unauthorized System") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = unAuthorizedSystem,
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("returns grant when authorized party matches grantedFor") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor,
            )
        )

        response.shouldBeRight(grant)
    }

    test("returns grant when authorized party matches grantedTo") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo,
            )
        )

        response.shouldBeRight(grant)
    }

    test("returns NotAuthorized when authorized party does not match grant") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor.copy(id = "other-person"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("returns NotAuthorized when authorized party does not match grantedTo") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo.copy(id = "other-org"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("maps grant repository not found to QueryError.ResourceNotFoundError") {
        val handler = Handler(repoReturning(result = RepositoryReadError.NotFoundError.left()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor,
            )
        )

        response.shouldBeLeft(QueryError.ResourceNotFoundError)
    }

    test("maps unexpected grant repository error to QueryError.IOError") {
        val handler = Handler(repoReturning(result = RepositoryReadError.UnexpectedError.left()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo,
            )
        )

        response.shouldBeLeft(QueryError.IOError)
    }
})
