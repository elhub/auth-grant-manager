package no.elhub.auth.features.documents.create

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.AuthorizationDocument.Status
import no.elhub.auth.features.documents.AuthorizationDocument.Type
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.documents.create.dto.CreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.postJson
import no.elhub.auth.setupAppWith
import no.elhub.auth.shouldBeValidUuid
import no.elhub.auth.validateInternalServerErrorResponse
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class RouteTest : FunSpec({
    val authorizedOrg = AuthorizedParty.OrganizationEntity(gln = "1", role = RoleType.BalanceSupplier)
    val byAuthParty = AuthorizationParty("gln1", PartyType.Organization)
    val fromAuthParty = AuthorizationParty("nin1", PartyType.Person)
    val toAuthParty = AuthorizationParty("nin2", PartyType.Person)
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
                    idValue = byAuthParty.id
                ),
                requestedFrom = PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = fromAuthParty.id
                ),
                requestedTo = PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = toAuthParty.id
                ),
                requestedFromName = "Hillary Orr",
                requestedForMeteringPointId = "123456789012345678",
                requestedForMeteringPointAddress = "quaerendum",
                balanceSupplierName = "Greatest Balance Supplier of All",
                balanceSupplierContractName = "Greatest Contract of All"
            )
        )
    )

    lateinit var authProvider: AuthorizationProvider
    lateinit var handler: Handler

    beforeAny {
        authProvider = mockk<AuthorizationProvider>()
        handler = mockk<Handler>()
    }

    test("POST / returns 201 when authorized as org and handler succeeds") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.postJson("/", examplePostBody)
            validateCreateDocumentResponse(result, examplePostBody)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns approprate error when authorization fails") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns AuthError.InvalidToken.left()
        coEvery { handler.invoke(any()) } returns document.right()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.postJson("/", examplePostBody)
            validateInvalidTokenResponse(result)
            coVerify(exactly = 0) { handler.invoke(any()) }
        }
    }

    test("POST / returns 400 Bad Request with 'Invalid national identity number' handler fails with InvalidNinError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.InvalidNinError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.postJson("/", examplePostBody)
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
    test("POST / returns 500 Internal Server Error when handler fails with FileGenerationError") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.FileGenerationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.postJson("/", examplePostBody)
            validateInternalServerErrorResponse(result)
            coVerify(exactly = 1) { handler.invoke(any()) }
        }
    }

    test("POST / returns 400 with detail about missing field when missing field in request body") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.FileGenerationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.postJson("/", createBodyWithMissingField)
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
    test("POST / returns 400 with specific field reference when invalid field value in request body") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedOrg.right()
        coEvery { handler.invoke(any()) } returns CreateError.FileGenerationError.left()
        testApplication {
            setupAppWith { route(handler, authProvider) }
            val result = client.postJson("/", createBodyWithInvalidFieldValue)
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

private suspend fun validateCreateDocumentResponse(response: HttpResponse, createBody: JsonApiCreateDocumentRequest) {
    val nowTolerance = Duration.ofSeconds(10)
    response.status shouldBe HttpStatusCode.Created
    val createDocumentResponse: CreateDocumentResponse = response.body()
    val defaultValidTo = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.plus(DatePeriod(days = 30))
    createDocumentResponse.data.apply {
        type shouldBe "AuthorizationDocument"
        id!!.shouldBeValidUuid()
        attributes.shouldNotBeNull().apply {
            documentType shouldBe createBody.data.attributes.documentType.toString()
            status shouldBe AuthorizationDocument.Status.Pending.name

            val createdAt = OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val updatedAt = OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val validTo = Instant.parse(validTo).toLocalDateTime(TimeZone.of("Europe/Oslo")).date

            validTo shouldBe defaultValidTo

            println(createdAt)
            println(currentTimeWithTimeZone())
            println(updatedAt)
            assertTrue(Duration.between(createdAt, currentTimeWithTimeZone()).abs() < nowTolerance)
            assertTrue(Duration.between(updatedAt, currentTimeWithTimeZone()).abs() < nowTolerance)
        }
        relationships.shouldNotBeNull().apply {
            requestedBy.apply {
                data.apply {
                    id shouldBe createBody.data.meta.requestedBy.idValue
                    type shouldBe "Organization"
                }
            }
            requestedFrom.apply {
                data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "Person"
                }
            }
            requestedTo.apply {
                data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "Person"
                }
            }
        }
        meta.shouldNotBeNull().apply {
            // From mocked handler
            values["key1"] shouldBe "value1"
            values["key2"] shouldBe "value2"
        }
        links.self shouldBe "$DOCUMENTS_PATH/$id"
        links.file shouldBe "$DOCUMENTS_PATH/$id.pdf"
    }
    createDocumentResponse.meta.shouldNotBeNull().apply {
        "createdAt".shouldNotBeNull()
    }
    val createdDocumentId = createDocumentResponse.data.id.toString()
    val linkToDocument = createDocumentResponse.data.links.self
    val linkToDocumentFile = createDocumentResponse.data.links.file
}
