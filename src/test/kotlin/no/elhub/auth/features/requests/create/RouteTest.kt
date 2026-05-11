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
import no.elhub.auth.features.common.currentTimeUtc
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
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

class RouteTest : FunSpec({
    val authorizedOrg = AuthorizationParty(id = "gln1", type = PartyType.OrganizationEntity)
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
        createdAt = currentTimeUtc(),
        updatedAt = currentTimeUtc(),
        validTo = currentTimeUtc().plusDays(30),
        properties = emptyList()
    )

    val examplePostBody = JsonApiCreateRequest(
        data = JsonApiRequestResourceObjectWithMeta(
            type = "AuthorizationRequest",
            attributes = CreateRequestAttributes(
                requestType = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson
            ),
            meta = CreateRequestMeta(
                requestedBy = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = requestedByParty.id),
                requestedFrom = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = requestedFromParty.id),
                requestedTo = PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = requestedToParty.id),
                requestedFromName = "John Doe",
                requestedForMeteringPointId = "123456789012345678",
                requestedForMeteringPointAddress = "Test Street 1",
                balanceSupplierName = "Test Balance Supplier",
                balanceSupplierContractName = "Test Contract",
                redirectURI = "https://example.com/redirect"
            )
        )
    )

    lateinit var handler: Handler

    beforeAny {
        handler = mockk<Handler>()
    }

    test("POST / returns 201 when authorized as BalanceSupplier org and handler succeeds") {
        coEvery { handler.invoke(any()) } returns authorizationRequest.right()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
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
            body.data.meta.values shouldBe authorizationRequest.properties.associate { it.key to it.value }
            body.data.links.self shouldBe "$REQUESTS_PATH/${authorizationRequest.id}"
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 201 when redirectURI is omitted from API request") {
        coEvery { handler.invoke(any()) } returns authorizationRequest.right()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            val bodyWithoutRedirect = examplePostBody.copy(
                data = examplePostBody.data.copy(meta = examplePostBody.data.meta.copy(redirectURI = null))
            )
            val response = client.postJson("/", bodyWithoutRedirect)
            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) { handler.invoke(withArg { it.businessMeta.redirectURI shouldBe null }) }
        }
    }

    test("POST / returns 409 Conflict when type in body is not AuthorizationRequest") {
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            val response = client.postJson("/", examplePostBody.copy(data = examplePostBody.data.copy(type = "WrongType")))
            response.status shouldBe HttpStatusCode.Conflict
        }
        coVerify(exactly = 0) { handler.invoke(any()) }
    }

    test("POST / returns 422 when handler fails with InvalidNinError") {
        coEvery { handler.invoke(any()) } returns CreateError.InvalidNinError.left()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            val response = client.postJson("/", examplePostBody)
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val responseJson: JsonApiErrorCollection = response.body()
            responseJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "422"
                    title shouldBe "Invalid national identity number"
                    detail shouldBe "Provided national identity number is invalid"
                }
            }
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 403 when handler fails with AuthorizationError") {
        coEvery { handler.invoke(any()) } returns CreateError.AuthorizationError.left()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
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
        coEvery { handler.invoke(any()) } returns CreateError.PersistenceError.left()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            validateInternalServerErrorResponse(client.postJson("/", examplePostBody))
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 500 when handler fails with MappingError") {
        coEvery { handler.invoke(any()) } returns CreateError.MappingError.left()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            validateInternalServerErrorResponse(client.postJson("/", examplePostBody))
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 500 when handler fails with RequestedPartyError") {
        coEvery { handler.invoke(any()) } returns CreateError.RequestedPartyError.left()
        testApplication {
            setupAppWith(authorizedOrg) { route(handler) }
            validateInternalServerErrorResponse(client.postJson("/", examplePostBody))
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }
})
