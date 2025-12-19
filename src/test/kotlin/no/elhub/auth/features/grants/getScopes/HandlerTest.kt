package no.elhub.auth.features.grants.getScopes

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.PermissionType
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
    val scopes =
        listOf(
            AuthorizationScope(
                id = 1,
                authorizedResourceType = ElhubResource.MeteringPoint,
                authorizedResourceId = "mp-1",
                permissionType = PermissionType.ReadAccess,
                createdAt = Instant.fromEpochMilliseconds(0),
            )
        )

    fun repoReturning(
        grantResult: arrow.core.Either<RepositoryReadError, AuthorizationGrant>,
        scopesResult: arrow.core.Either<RepositoryReadError, List<AuthorizationScope>> = scopes.right(),
    ): GrantRepository =
        mockk<GrantRepository> {
            every { find(grantId) } returns grantResult
            every { findScopes(grantId) } returns scopesResult
        }

    test("returns scopes when authorized party matches grantedFor") {
        val handler = Handler(repoReturning(grantResult = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor,
            )
        )

        response.shouldBeRight(scopes)
    }

    test("returns scopes when authorized party matches grantedTo") {
        val handler = Handler(repoReturning(grantResult = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo,
            )
        )

        response.shouldBeRight(scopes)
    }

    test("returns NotAuthorized when authorized party does not match grant") {
        val handler = Handler(repoReturning(grantResult = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor.copy(resourceId = "other-person"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("returns NotAuthorized when authorized party does not match grantedTo") {
        val handler = Handler(repoReturning(grantResult = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo.copy(resourceId = "other-org"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("maps grant repository not found to QueryError.ResourceNotFoundError") {
        val handler = Handler(repoReturning(grantResult = RepositoryReadError.NotFoundError.left()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor,
            )
        )

        response.shouldBeLeft(QueryError.ResourceNotFoundError)
    }

    test("maps unexpected grant repository error to QueryError.IOError") {
        val handler = Handler(repoReturning(grantResult = RepositoryReadError.UnexpectedError.left()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo,
            )
        )

        response.shouldBeLeft(QueryError.IOError)
    }

    test("maps scopes repository not found to QueryError.ResourceNotFoundError") {
        val handler =
            Handler(
                repoReturning(
                    grantResult = grant.right(),
                    scopesResult = RepositoryReadError.NotFoundError.left(),
                )
            )

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedFor,
            )
        )

        response.shouldBeLeft(QueryError.ResourceNotFoundError)
    }

    test("maps unexpected scopes repository error to QueryError.IOError") {
        val handler =
            Handler(
                repoReturning(
                    grantResult = grant.right(),
                    scopesResult = RepositoryReadError.UnexpectedError.left(),
                )
            )

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo,
            )
        )

        response.shouldBeLeft(QueryError.IOError)
    }
})
