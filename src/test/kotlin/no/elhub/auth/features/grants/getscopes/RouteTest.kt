package no.elhub.auth.features.grants.getscopes

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.dto.AuthorizationGrantScopesResponse
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.auth.validateNotAuthorizedResponse
import no.elhub.auth.validateNotFoundResponse
import java.util.UUID

class RouteTest : FunSpec({
    lateinit var handler: Handler
    lateinit var authProvider: AuthorizationProvider

    val validUuid = "02fe286b-4519-4ba8-9c84-dc18bffc9eb3"
    val authorizedSystem = AuthorizedParty.System(id = "id")
    val authorizedPerson = AuthorizedParty.Person(id = UUID.fromString("1d024a64-abb0-47d1-9b81-5d98aaa1a8a9"))
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "1", role = RoleType.BalanceSupplier)

    val scope = AuthorizationScope(
        id = UUID.fromString("8844261a-5221-455c-a6cd-12a0d60724c2"),
        authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
        authorizedResourceId = "resource-id",
        permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson,
        createdAt = currentTimeWithTimeZone()
    )
    val expectedScopes = listOf(scope)

    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("GET /{id}/scopes returns 401 when authorization fails with InvalidToken") {
        coEvery { authProvider.authorizeAll(any()) } returns AuthError.InvalidToken.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.Unauthorized
            coVerify(exactly = 0) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 401 when authorization fails with NotAuthorized") {
        coEvery { authProvider.authorizeAll(any()) } returns AuthError.NotAuthorized.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            validateNotAuthorizedResponse(response)
            coVerify(exactly = 0) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 403 when authorization fails with AccessDenied") {
        coEvery { authProvider.authorizeAll(any()) } returns AuthError.AccessDenied.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            validateForbiddenResponse(response)
            coVerify(exactly = 0) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 400 when id is not a valid UUID") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            validateMalformedInputResponse(client.get("/not-a-uuid/scopes"))
            coVerify(exactly = 0) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 404 when handler returns ResourceNotFoundError") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        coEvery { handler(any()) } returns QueryError.ResourceNotFoundError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            validateNotFoundResponse(response)
            coVerify(exactly = 1) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 500 when handler returns IOError") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        coEvery { handler(any()) } returns QueryError.IOError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            validateInternalServerErrorResponse(response)
            coVerify(exactly = 1) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 200 and correct body when authorized as system and handler succeeds") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        coEvery { handler(any()) } returns expectedScopes.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data.size shouldBe 1
            body.data.first().apply {
                id shouldBe scope.id.toString()
                type shouldBe "AuthorizationScope"
                attributes!!.permissionType shouldBe scope.permissionType
                relationships.authorizedResources.data.first().apply {
                    id shouldBe scope.authorizedResourceId
                    type shouldBe scope.authorizedResourceType.name
                }
            }
            coVerify(exactly = 1) { handler(match { it.authorizedParty.id == authorizedSystem.id }) }
        }
    }

    test("GET /{id}/scopes returns 200 and correct body when authorized as person and handler succeeds") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedPerson.right()
        coEvery { handler(any()) } returns expectedScopes.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data.size shouldBe 1
            body.data.first().id shouldBe scope.id.toString()
            coVerify(exactly = 1) { handler(match { it.authorizedParty.id == authorizedPerson.id.toString() }) }
        }
    }

    test("GET /{id}/scopes returns 200 and correct body when authorized as org and handler succeeds") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedOrg.right()
        coEvery { handler(any()) } returns expectedScopes.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data.size shouldBe 1
            body.data.first().id shouldBe scope.id.toString()
            coVerify(exactly = 1) { handler(match { it.authorizedParty.id == authorizedOrg.gln }) }
        }
    }

    test("GET /{id}/scopes returns 200 with empty list when handler returns no scopes") {
        coEvery { authProvider.authorizeAll(any()) } returns authorizedSystem.right()
        coEvery { handler(any()) } returns emptyList<AuthorizationScope>().right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data shouldBe emptyList()
            coVerify(exactly = 1) { handler(any()) }
        }
    }
})
