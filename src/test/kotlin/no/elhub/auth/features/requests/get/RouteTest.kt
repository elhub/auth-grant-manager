package no.elhub.auth.features.requests.get

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
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
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.get.dto.GetRequestSingleResponse
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateForbiddenResponse
import no.elhub.auth.validateMalformedInputResponse
import no.elhub.auth.validateNotAuthorizedResponse
import no.elhub.auth.validateNotFoundResponse
import java.util.UUID

private const val TEXT_VERSION_KEY = "textVersion"

class RouteTest : FunSpec({

    val authorizedPerson = AuthorizedParty.Person(id = UUID.randomUUID())
    val authorizedOrganization = AuthorizedParty.OrganizationEntity(gln = "1234567890123", role = RoleType.BalanceSupplier)
    val validUuid = "02fe286b-4519-4ba8-9c84-dc18bffc9eb3"

    val requestedByParty = AuthorizationParty("gln1", PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty("nin1", PartyType.Person)
    val requestedToParty = AuthorizationParty("nin2", PartyType.Person)

    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler

    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("GET /{id} should return forbidden when access is denied") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns AuthError.AccessDenied.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            validateForbiddenResponse(response)
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("GET /{id} should return unauthorized when not authorized") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns AuthError.NotAuthorized.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            validateNotAuthorizedResponse(response)
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("GET /{id} should return bad request when having an invalid request ID") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/not-a-uuid")
            validateMalformedInputResponse(response)
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("GET /{id} should return not found when handler returns ResourceNotFoundError") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns QueryError.ResourceNotFoundError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            validateNotFoundResponse(response)
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} should return forbidden when handler returns NotAuthorizedError") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } returns QueryError.NotAuthorizedError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.Forbidden
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} should return 500 when handler throws exception") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        coEvery { handler.invoke(any()) } throws RuntimeException("Unexpected error")
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.InternalServerError
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} should return OK with correct body when authorized as Person") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedPerson.right()
        val authorizationRequest = AuthorizationRequest(
            id = UUID.fromString(validUuid),
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            status = AuthorizationRequest.Status.Pending,
            validTo = currentTimeUtc(),
            createdAt = currentTimeUtc(),
            updatedAt = currentTimeUtc(),
            requestedBy = requestedByParty,
            requestedTo = requestedToParty,
            requestedFrom = requestedFromParty,
            properties = emptyList()
        )

        coEvery { handler.invoke(any()) } returns authorizationRequest.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<GetRequestSingleResponse>()
            body.data.id shouldBe authorizationRequest.id.toString()
            body.data.type shouldBe "AuthorizationRequest"
            body.data.attributes.status shouldBe "Pending"
            body.data.attributes.requestType shouldBe "ChangeOfBalanceSupplierForPerson"
            body.data.attributes.validTo.shouldNotBeBlank()
            body.data.attributes.createdAt.shouldNotBeBlank()
            body.data.attributes.updatedAt.shouldNotBeBlank()
            body.data.relationships.requestedBy.data.id shouldBe requestedByParty.id
            body.data.relationships.requestedBy.data.type shouldBe requestedByParty.type.name
            body.data.relationships.requestedFrom.data.id shouldBe requestedFromParty.id
            body.data.relationships.requestedFrom.data.type shouldBe requestedFromParty.type.name
            body.data.relationships.requestedTo.data.id shouldBe requestedToParty.id
            body.data.relationships.requestedTo.data.type shouldBe requestedToParty.type.name
            body.data.relationships.approvedBy.shouldBeNull()
            body.data.relationships.authorizationGrant.shouldBeNull()
            body.data.meta.values shouldBe emptyMap()
            body.data.links.self shouldBe "$REQUESTS_PATH/${authorizationRequest.id}"
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }

    test("GET /{id} should return OK with correct body when authorized as OrganizationEntity") {
        coEvery { authProvider.authorizeEndUserOrMaskinporten(any()) } returns authorizedOrganization.right()
        val authorizationRequest = AuthorizationRequest(
            id = UUID.fromString(validUuid),
            type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
            status = AuthorizationRequest.Status.Accepted,
            validTo = currentTimeUtc(),
            createdAt = currentTimeUtc(),
            updatedAt = currentTimeUtc(),
            requestedBy = requestedByParty,
            requestedTo = requestedToParty,
            requestedFrom = requestedFromParty,
            approvedBy = AuthorizationParty("nin1", PartyType.Person),
            grantId = UUID.randomUUID(),
            properties = listOf(
                AuthorizationRequestProperty(UUID.fromString(validUuid), "requestedFromName", "Test Person"),
                AuthorizationRequestProperty(UUID.fromString(validUuid), "balanceSupplierName", "Power AS"),
                AuthorizationRequestProperty(UUID.fromString(validUuid), TEXT_VERSION_KEY, "v1")
            )
        )

        coEvery { handler.invoke(any()) } returns authorizationRequest.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.get("/$validUuid")
            response.status shouldBe HttpStatusCode.OK
            val body = response.body<GetRequestSingleResponse>()
            body.data.id shouldBe authorizationRequest.id.toString()
            body.data.type shouldBe "AuthorizationRequest"
            body.data.attributes.status shouldBe "Accepted"
            body.data.attributes.requestType shouldBe "ChangeOfBalanceSupplierForPerson"
            body.data.attributes.validTo.shouldNotBeBlank()
            body.data.attributes.createdAt.shouldNotBeBlank()
            body.data.attributes.updatedAt.shouldNotBeBlank()
            body.data.relationships.requestedBy.data.id shouldBe requestedByParty.id
            body.data.relationships.requestedBy.data.type shouldBe requestedByParty.type.name
            body.data.relationships.requestedFrom.data.id shouldBe requestedFromParty.id
            body.data.relationships.requestedFrom.data.type shouldBe requestedFromParty.type.name
            body.data.relationships.requestedTo.data.id shouldBe requestedToParty.id
            body.data.relationships.requestedTo.data.type shouldBe requestedToParty.type.name
            body.data.relationships.approvedBy!!.data.id shouldBe "nin1"
            body.data.relationships.approvedBy!!.data.type shouldBe "Person"
            body.data.relationships.authorizationGrant!!.data.id shouldBe authorizationRequest.grantId.toString()
            body.data.relationships.authorizationGrant!!.data.type shouldBe "AuthorizationGrant"
            body.data.meta.values["requestedFromName"] shouldBe "Test Person"
            body.data.meta.values["balanceSupplierName"] shouldBe "Power AS"
            body.data.meta.values[TEXT_VERSION_KEY] shouldBe "v1"
            body.data.links.self shouldBe "$REQUESTS_PATH/${authorizationRequest.id}"
        }
        coVerify(exactly = 1) { handler.invoke(any()) }
    }
})
