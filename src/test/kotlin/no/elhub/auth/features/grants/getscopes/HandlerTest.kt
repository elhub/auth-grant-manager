package no.elhub.auth.features.grants.getscopes

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class HandlerTest : FunSpec({

    val authorizedSystem = AuthorizationParty(id = "some-elhub-service", type = PartyType.System)
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
            grantedAt = currentTimeUtc(),
            validFrom = currentTimeUtc(),
            createdAt = currentTimeUtc(),
            updatedAt = currentTimeUtc(),
            validTo = currentTimeUtc().plusYears(1),
            sourceType = SourceType.Document,
            sourceId = UUID.randomUUID(),
            scopeIds = scopeIds,
            properties = emptyList()
        )
    val scopes =
        listOf(
            AuthorizationScope(
                id = UUID.randomUUID(),
                authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                authorizedResourceId = "mp-1",
                permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson,
                createdAt = currentTimeUtc(),
            )
        )

    fun repoReturning(
        grantResult: arrow.core.Either<RepositoryReadError, AuthorizationGrant>,
        scopesResult: arrow.core.Either<RepositoryReadError, List<AuthorizationScope>> = scopes.right(),
    ): GrantRepository =
        mockk<GrantRepository> {
            coEvery { find(grantId) } returns grantResult
            coEvery { findScopes(grantId) } returns scopesResult
        }

    test("returns scopes when authorized party is valid System") {
        val handler = Handler(repoReturning(grantResult = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = authorizedSystem,
            )
        )

        response.shouldBeRight(scopes)
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
                authorizedParty = grantedFor.copy(id = "other-person"),
            )
        )

        response.shouldBeLeft(QueryError.NotAuthorizedError)
    }

    test("returns NotAuthorized when authorized party does not match grantedTo") {
        val handler = Handler(repoReturning(grantResult = grant.right()))

        val response = handler(
            Query(
                id = grantId,
                authorizedParty = grantedTo.copy(id = "other-org"),
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
