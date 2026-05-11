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
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.dto.AuthorizationGrantScopesResponse
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.auth.validateNotFoundResponse
import java.util.UUID

class RouteTest : FunSpec({
    lateinit var handler: Handler

    val validUuid = "02fe286b-4519-4ba8-9c84-dc18bffc9eb3"
    val authorizedSystem = AuthorizationParty(id = "id", type = PartyType.System)
    val authorizedPerson = AuthorizationParty(id = "1d024a64-abb0-47d1-9b81-5d98aaa1a8a9", type = PartyType.Person)
    val authorizedOrg = AuthorizationParty(id = "1", type = PartyType.OrganizationEntity)

    val scope = AuthorizationScope(
        id = UUID.fromString("8844261a-5221-455c-a6cd-12a0d60724c2"),
        authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
        authorizedResourceId = "resource-id",
        permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson,
        createdAt = currentTimeUtc()
    )
    val expectedScopes = listOf(scope)

    beforeAny {
        handler = mockk<Handler>()
    }

    test("GET /{id}/scopes returns 400 when id is not a valid UUID") {
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            validateMalformedInputResponse(client.get("/not-a-uuid/scopes"))
            coVerify(exactly = 0) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 404 when handler returns ResourceNotFoundError") {
        coEvery { handler(any()) } returns QueryError.ResourceNotFoundError.left()
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            validateNotFoundResponse(client.get("/$validUuid/scopes"))
            coVerify(exactly = 1) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 500 when handler returns IOError") {
        coEvery { handler(any()) } returns QueryError.IOError.left()
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            validateInternalServerErrorResponse(client.get("/$validUuid/scopes"))
            coVerify(exactly = 1) { handler(any()) }
        }
    }

    test("GET /{id}/scopes returns 200 and correct body when authorized as system") {
        coEvery { handler(any()) } returns expectedScopes.right()
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
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

    test("GET /{id}/scopes returns 200 and correct body when authorized as person") {
        coEvery { handler(any()) } returns expectedScopes.right()
        testApplication {
            setupAppWith(authorizedPerson) { route(handler) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data.size shouldBe 1
            body.data.first().id shouldBe scope.id.toString()
            coVerify(exactly = 1) { handler(match { it.authorizedParty.id == authorizedPerson.id }) }
        }
    }

    test("GET /{id}/scopes returns 200 and correct body when authorized as org") {
        coEvery { handler(any()) } returns expectedScopes.right()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data.size shouldBe 1
            body.data.first().id shouldBe scope.id.toString()
            coVerify(exactly = 1) { handler(match { it.authorizedParty.id == authorizedOrg.id }) }
        }
    }

    test("GET /{id}/scopes returns 200 with empty list when handler returns no scopes") {
        coEvery { handler(any()) } returns emptyList<AuthorizationScope>().right()
        testApplication {
            setupAppWith(authorizedSystem) { route(handler) }
            val response = client.get("/$validUuid/scopes")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<AuthorizationGrantScopesResponse>()
            body.data shouldBe emptyList()
            coVerify(exactly = 1) { handler(any()) }
        }
    }
})
