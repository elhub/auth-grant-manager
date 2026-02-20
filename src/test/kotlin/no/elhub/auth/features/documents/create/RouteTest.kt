package no.elhub.auth.features.documents.create

import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.auth.features.documents.common.SignatureValidationError

import io.kotest.matchers.string.shouldBeEmpty
import no.elhub.auth.features.documents.module

import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

import io.ktor.serialization.kotlinx.json.json
import arrow.core.Either
import arrow.core.left
import io.ktor.client.request.put
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import arrow.core.right
import no.elhub.auth.setupAppWith
import io.kotest.assertions.arrow.core.shouldBeLeft
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.auth.module as applicationModule
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.tuple
import io.mockk.every
import io.mockk.coEvery
import kotlin.random.Random
import io.mockk.mockk
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.QueryError
import io.ktor.server.testing.testApplication
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.AuthorizationDocument.Type
import no.elhub.auth.features.documents.AuthorizationDocument.Status
import io.ktor.http.HttpStatusCode
import io.ktor.client.call.body
import io.ktor.http.ContentType
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.toByteArray
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.elhub.auth.config.configureErrorHandling
import no.elhub.auth.config.configureSerialization
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.auth.validateNotAuthorizedResponse

class RouteTest : FunSpec({
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "1", role = RoleType.BalanceSupplier)
    val byAuthParty = AuthorizationParty("id1", PartyType.Organization)
    val fromAuthParty = AuthorizationParty("id2", PartyType.Person)
    val toAuthParty = AuthorizationParty("id3", PartyType.Person)
    val document = AuthorizationDocument(
        id = UUID.fromString("b5b61d43-6e35-4b30-aa4d-48f506be5af4"),
        type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
        status = AuthorizationDocument.Status.Pending,
        file = Random.nextBytes(256),
        requestedBy = byAuthParty,
        requestedFrom = fromAuthParty,
        requestedTo = toAuthParty,
        signedBy = null,
        grantId = UUID.fromString("8844261a-5221-455c-a6cd-12a0d60724c2"),
        properties = listOf(
            AuthorizationDocumentProperty("key1", "value1"),
            AuthorizationDocumentProperty("key2", "value2"),
        ),
        validTo = currentTimeWithTimeZone().plusDays(30),
        createdAt = currentTimeWithTimeZone(),
        updatedAt = currentTimeWithTimeZone()
    )

    val examplePostBody = JsonApiCreateDocumentRequest(
        data = JsonApiRequestResourceObjectWithMeta(
            type = "AuthorizationDocument",
            attributes = CreateDocumentRequestAttributes(
                documentType = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson
            ),
            meta = CreateDocumentMeta(
                requestedBy = PartyIdentifier(
                    idType = PartyIdentifierType.GlobalLocationNumber,
                    idValue = "0107000000021"
                ),
                requestedFrom = PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = REQUESTED_FROM_NIN
                ),
                requestedTo = PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = REQUESTED_TO_NIN
                ),
                requestedFromName = "Hillary Orr",
                requestedForMeteringPointId = "123456789012345678",
                requestedForMeteringPointAddress = "quaerendum",
                balanceSupplierName = "Jami Wade",
                balanceSupplierContractName = "Selena Chandler"
            )
        )
    )
    test("POST / returns 201 when authorized as org and handler succeeds") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(examplePostBody)
            }
            result.status shouldBe HttpStatusCode.Created
            // TODO validate body!

            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns approprate error when authorization fails") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns AuthError.InvalidToken.left()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(examplePostBody)
            }
            validateInvalidTokenResponse(result)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / returns appropriate error when handler fails (InvalidNinError)") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.InvalidNinError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(examplePostBody)
            }
            result.status shouldBe HttpStatusCode.BadRequest
            val resultJson: JsonApiErrorCollection = result.body()
            resultJson.errors.apply {
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
    test("POST / returns appropriate error when handler fails (FileGenerationError)") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.FileGenerationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(examplePostBody)
            }
            validateInternalServerErrorResponse(result)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns appropriate error when missing field in request body") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.FileGenerationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(createBodyWithMissingField)
            }
            result.status shouldBe HttpStatusCode.BadRequest
            val resultJson: JsonApiErrorCollection = result.body()
            resultJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "400"
                    title shouldBe "Missing required field in request body"
                    detail shouldBe "Field '[idValue]' is missing or invalid"
                }
            }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
    test("POST / returns appropriate error when invalid field value in request body") {
        val authProvider = mockk<AuthorizationProvider>()
        val handler = mockk<Handler>()
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.FileGenerationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.post("/") {
                contentType(ContentType.Application.Json)
                setBody(createBodyWithInvalidFieldValue)
            }
            result.status shouldBe HttpStatusCode.BadRequest
            val resultJson: JsonApiErrorCollection = result.body()
            resultJson.errors.apply {
                size shouldBe 1
                this[0].apply {
                    status shouldBe "400"
                    title shouldBe "Invalid field value in request body"
                    detail shouldBe "Invalid value 'TEST' for field 'data' at $.data.meta.requestedBy.idType"
                }
            }
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }
})

private const val REQUESTED_FROM_NIN = "02916297702"
private const val REQUESTED_TO_NIN = "14810797496"
private val createBodyWithMissingField = """
    {
      "data": {
        "type": "AuthorizationDocument"
        "attributes": {
          "documentType": "ChangeOfEnergySupplierForPerson"
        },
        "meta": {
          "requestedBy": { "idType": "GlobalLocationNumber" },
          "requestedFrom": { "idType": "NationalIdentityNumber", "idValue": "$REQUESTED_FROM_NIN" },
          "requestedTo": { "idType": "NationalIdentityNumber", "idValue": "$REQUESTED_TO_NIN" },
          "requestedFromName": "Hillary Orr",
          "requestedForMeteringPointId": "123456789012345678",
          "requestedForMeteringPointAddress": "quaerendum",
          "balanceSupplierName": "Balance Supplier",
          "balanceSupplierContractName": "Selena Chandler",
          "redirectURI": "https://example.com/redirect"
        }
      }
    }
""".trimIndent()
private val createBodyWithInvalidFieldValue = """
    {
      "data": {
        "type": "AuthorizationDocument",
        "attributes": {
          "documentType": "ChangeOfEnergySupplierForPerson"
        },
        "meta": {
          "requestedBy": { "idType": "TEST", "idValue": "0107000000021" },
          "requestedFrom": { "idType": "NationalIdentityNumber", "idValue": "$REQUESTED_FROM_NIN" },
          "requestedTo": { "idType": "NationalIdentityNumber", "idValue": "$REQUESTED_TO_NIN" },
          "requestedFromName": "Hillary Orr",
          "requestedForMeteringPointId": "123456789012345678",
          "requestedForMeteringPointAddress": "quaerendum",
          "balanceSupplierName": "Balance Supplier",
          "balanceSupplierContractName": "Selena Chandler",
          "redirectURI": "https://example.com/redirect"
        }
      }
    }
""".trimIndent()
