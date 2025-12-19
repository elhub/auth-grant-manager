package no.elhub.auth.features.grants.get

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.common.GrantRepository
import java.time.LocalDateTime
import java.util.UUID

class HandlerTest : FunSpec({

    val grantedFor = AuthorizationParty(resourceId = "person-1", type = PartyType.Person)
    val grantedBy = AuthorizationParty(resourceId = "issuer-1", type = PartyType.Organization)
    val grantedTo = AuthorizationParty(resourceId = "org-entity-1", type = PartyType.OrganizationEntity)
    val grantId = UUID.randomUUID()
    val grant =
        AuthorizationGrant(
            id = grantId,
            grantStatus = Status.Active,
            grantedFor = grantedFor,
            grantedBy = grantedBy,
            grantedTo = grantedTo,
            grantedAt = LocalDateTime.now(),
            validFrom = LocalDateTime.now(),
            validTo = LocalDateTime.now().plusYears(1),
            sourceType = SourceType.Document,
            sourceId = UUID.randomUUID(),
        )

    fun repoReturning(result: arrow.core.Either<RepositoryReadError, AuthorizationGrant>): GrantRepository =
        mockk<GrantRepository> {
            every { find(grantId) } returns result
        }

    test("returns grant when requester matches grantedFor") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query.GrantedFor(
                id = grantId,
                grantedFor = grantedFor,
            )
        )

        response.shouldBeRight(grant)
    }

    test("returns grant when requester matches grantedTo") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query.GrantedTo(
                id = grantId,
                grantedTo = grantedTo,
            )
        )

        response.shouldBeRight(grant)
    }

    test("returns NotAuthorized when requester does not match grant") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query.GrantedFor(
                id = grantId,
                grantedFor = grantedFor.copy(resourceId = "other-person"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("returns NotAuthorized when grantedTo does not match grant") {
        val handler = Handler(repoReturning(result = grant.right()))

        val response = handler(
            Query.GrantedTo(
                id = grantId,
                grantedTo = grantedTo.copy(resourceId = "other-org"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("maps repository not found to QueryError.ResourceNotFoundError") {
        val handler = Handler(repoReturning(result = RepositoryReadError.NotFoundError.left()))

        val response = handler(
            Query.GrantedFor(
                id = grantId,
                grantedFor = grantedFor,
            )
        )

        response.shouldBeLeft(QueryError.ResourceNotFoundError)
    }

    test("maps unexpected repository error to QueryError.IOError") {
        val handler = Handler(repoReturning(result = RepositoryReadError.UnexpectedError.left()))

        val response = handler(
            Query.GrantedTo(
                id = grantId,
                grantedTo = grantedTo,
            )
        )

        response.shouldBeLeft(QueryError.IOError)
    }
})
