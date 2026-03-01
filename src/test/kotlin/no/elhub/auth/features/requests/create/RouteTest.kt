package no.elhub.auth.features.requests.create

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.create.dto.CreateRequestAttributes
import no.elhub.auth.features.requests.create.dto.CreateRequestMeta
import no.elhub.auth.features.requests.create.dto.CreateRequestResponse
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.postJson
import no.elhub.auth.setupAppWith
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

class RouteTest : FunSpec({
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "gln1", role = RoleType.BalanceSupplier)
    val requestedByParty = AuthorizationParty("gln1", PartyType.OrganizationEntity)
    val requestedFromParty = AuthorizationParty("nin1", PartyType.Person)
    val requestedToParty = AuthorizationParty("nin2", PartyType.Person)

    val authorizationRequest = AuthorizationRequest(
        id = UUID.fromString("b5b61d43-6e35-4b30-aa4d-48f506be5af4"),
        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
        status = AuthorizationRequest.Status.Pending,
        requestedBy = requestedByParty,
        requestedFrom = requestedFromParty,
        requestedTo = requestedToParty,
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone(),
        validTo = currentTimeWithTimeZone().plusDays(30),
        properties = emptyList()
    )

    val examplePostBody = JsonApiCreateRequest(
        data = JsonApiRequestResourceObjectWithMeta(
            type = "AuthorizationRequest",
            attributes = CreateRequestAttributes(
                requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson
            ),
            meta = CreateRequestMeta(
                requestedBy = PartyIdentifier(
                    idType = PartyIdentifierType.GlobalLocationNumber,
                    idValue = requestedByParty.id
                ),
                requestedFrom = PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = requestedFromParty.id
                ),
                requestedTo = PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = requestedToParty.id
                ),
                requestedFromName = "John Doe",
                requestedForMeteringPointId = "123456789012345678",
                requestedForMeteringPointAddress = "Test Street 1",
                balanceSupplierName = "Test Balance Supplier",
                balanceSupplierContractName = "Test Contract",
                redirectURI = "https://example.com/redirect"
            )
        )
    )

    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler

    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("POST / returns 201 when authorized as BalanceSupplier org and handler succeeds") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns authorizationRequest.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            response.status shouldBe HttpStatusCode.Created
            val body = response.body<CreateRequestResponse>()
            body.data.type shouldBe examplePostBody.data.type
            body.data.attributes.requestType shouldBe examplePostBody.data.attributes.requestType.name
            body.data.attributes.status shouldBe authorizationRequest.status.name
            body.data.attributes.createdAt shouldNotBe null
            body.data.attributes.updatedAt shouldNotBe null
            body.data.attributes.validTo shouldNotBe null

            body.data.relationships.requestedBy.data.id shouldBe requestedByParty.id
            body.data.relationships.requestedBy.data.type shouldBe requestedByParty.type.name

            body.data.relationships.requestedFrom.data.id shouldBe requestedFromParty.id
            body.data.relationships.requestedFrom.data.type shouldBe requestedFromParty.type.name

            body.data.relationships.requestedTo.data.id shouldBe requestedToParty.id
            body.data.relationships.requestedTo.data.type shouldBe requestedToParty.type.name

            body.data.relationships.requestedFrom.data.id shouldBe requestedFromParty.id
            body.data.relationships.requestedFrom.data.type shouldBe requestedFromParty.type.name
            body.data.relationships.requestedTo.data.id shouldBe requestedToParty.id
            body.data.relationships.requestedTo.data.type shouldBe requestedToParty.type.name
            body.data.relationships.requestedBy.data.id shouldBe requestedByParty.id
            body.data.relationships.requestedBy.data.type shouldBe requestedByParty.type.name

            body.data.meta.values shouldBe authorizationRequest.properties.associate { it.key to it.value }
            body.data.links.self shouldBe "$REQUESTS_PATH/${authorizationRequest.id}"
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 401 when authorization fails with InvalidToken") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns AuthError.InvalidToken.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            validateInvalidTokenResponse(response)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / returns 409 Conflict when type in body is not AuthorizationRequest") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val wrongTypeBody = examplePostBody.copy(
                data = examplePostBody.data.copy(type = "WrongType")
            )
            val response = client.postJson("/", wrongTypeBody)
            response.status shouldBe HttpStatusCode.Conflict
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / returns 400 when handler fails with InvalidNinError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.InvalidNinError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            response.status shouldBe HttpStatusCode.BadRequest
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "400"
                    title shouldBe "Invalid national identity number"
                    detail shouldBe "Provided national identity number is invalid"
                }
            }
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 403 when handler fails with AuthorizationError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.AuthorizationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            response.status shouldBe HttpStatusCode.Forbidden
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "403"
                    title shouldBe "Party not authorized"
                    detail shouldBe "RequestedBy must match the authorized party"
                }
            }
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 500 when handler fails with PersistenceError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.PersistenceError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            validateInternalServerErrorResponse(response)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 500 when handler fails with MappingError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.MappingError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            validateInternalServerErrorResponse(response)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 500 when handler fails with RequestedPartyError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.RequestedPartyError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.postJson("/", examplePostBody)
            validateInternalServerErrorResponse(response)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
})
