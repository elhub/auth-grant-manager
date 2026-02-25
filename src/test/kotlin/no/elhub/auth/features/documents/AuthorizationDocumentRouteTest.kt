package no.elhub.auth.features.documents

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.defaultValidTo
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.today
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.documents.create.dto.CreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.features.documents.create.dto.SupportedLanguageDTO
import no.elhub.auth.features.documents.create.dto.toSupportedLanguage
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponse
import no.elhub.auth.features.documents.query.dto.GetDocumentCollectionResponse
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.grants.common.dto.AuthorizationGrantScopesResponse
import no.elhub.auth.features.grants.common.dto.SingleGrantResponse
import no.elhub.auth.shouldBeValidUuid
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.plugin.koinModule
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import no.elhub.auth.features.grants.module as grantsModule
import no.elhub.auth.module as applicationModule

// These testing are sharing data so only works by running all tests in chronological order
class AuthorizationDocumentRouteTest :
    FunSpec({
        val pdpContainer = PdpTestContainerExtension()

        extensions(
            PostgresTestContainerExtension(),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
            VaultTransitTestContainerExtension,
            AuthPersonsTestContainerExtension,
            pdpContainer
        )

        beforeSpec {
            pdpContainer.registerMaskinportenMapping(
                token = "maskinporten",
                actingGln = "0107000000021",
                functionName = "BalanceSupplier"
            )
        }

        lateinit var createdDocumentId: String
        lateinit var expectedSignatory: String
        lateinit var linkToDocument: String
        lateinit var linkToDocumentFile: String
        lateinit var signedFile: ByteArray
        lateinit var grantId: String
        lateinit var requestedFromId: String
        lateinit var requestedToId: String

        val nowTolerance = Duration.ofSeconds(5)

        context("Run document happy flow") {
            testApplication {
                client = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                application {
                    applicationModule()
                    testBusinessProcessesModule()
                    commonModule()
                    grantsModule()
                    module()
                }

                environment {
                    config = MapApplicationConfig(
                        "ktor.database.username" to "app",
                        "ktor.database.password" to "app",
                        "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
                        "ktor.database.driverClass" to "org.postgresql.Driver",
                        "pdfGenerator.mustacheResourcePath" to "templates",
                        "pdfGenerator.useTestPdfNotice" to "true",
                        "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
                        "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
                        "pdfSigner.vault.key" to "test-key",
                        "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.bankIdRootDir" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATES_DIR,
                        "featureToggle.enableEndpoints" to "true",
                        "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
                        "pdp.baseUrl" to "http://localhost:8085"
                    )
                }

                test("Should return 409 Conflict on invalid data.type") {
                    val response =
                        client.post(DOCUMENTS_PATH) {
                            header(HttpHeaders.Authorization, "Bearer maskinporten")
                            header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                {
                  "data": {
                    "type": "test"
                    "attributes": {
                      "documentType": "ChangeOfBalanceSupplierForPerson"
                    },
                    "meta": {
                      "requestedBy": { "idType": "GlobalLocationNumber", "idValue": "0107000000021" },
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
                            )
                        }

                    response.status shouldBe HttpStatusCode.Conflict

                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "409"
                            title shouldBe "Resource type mismatch"
                            detail shouldBe "Expected 'data.type' to be 'AuthorizationDocument', but received 'test'"
                        }
                    }
                    responseJson.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Should return 400 Bad Request on missing field in request body") {
                    val response =
                        client.post(DOCUMENTS_PATH) {
                            header(HttpHeaders.Authorization, "Bearer maskinporten")
                            header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                {
                  "data": {
                    "type": "AuthorizationDocument"
                    "attributes": {
                      "documentType": "ChangeOfBalanceSupplierForPerson"
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
                            )
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "400"
                            title shouldBe "Missing required field in request body"
                            detail shouldBe "Field '[idValue]' is missing or invalid"
                        }
                    }
                    responseJson.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Should return 400 Bad Request on invalid field value in request body") {
                    val response =
                        client.post(DOCUMENTS_PATH) {
                            header(HttpHeaders.Authorization, "Bearer maskinporten")
                            header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                {
                  "data": {
                    "type": "AuthorizationDocument",
                    "attributes": {
                      "documentType": "ChangeOfBalanceSupplierForPerson"
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
                            )
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "400"
                            title shouldBe "Invalid field value in request body"
                            detail shouldBe "Invalid value 'TEST' for field 'data' at $.data.meta.requestedBy.idType"
                        }
                    }
                    responseJson.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Should return 400 Bad Request on invalid language field in request body") {
                    val response =
                        client.post(DOCUMENTS_PATH) {
                            header(HttpHeaders.Authorization, "Bearer maskinporten")
                            header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                {
                  "data": {
                    "type": "AuthorizationDocument",
                    "attributes": {
                      "documentType": "ChangeOfBalanceSupplierForPerson"
                    },
                    "meta": {
                      "requestedBy": { "idType": "GlobalLocationNumber", "idValue": "0107000000021" },
                      "requestedFrom": { "idType": "NationalIdentityNumber", "idValue": "$REQUESTED_FROM_NIN" },
                      "requestedTo": { "idType": "NationalIdentityNumber", "idValue": "$REQUESTED_TO_NIN" },
                      "requestedFromName": "Hillary Orr",
                      "requestedForMeteringPointId": "123456789012345678",
                      "requestedForMeteringPointAddress": "quaerendum",
                      "balanceSupplierName": "Balance Supplier",
                      "balanceSupplierContractName": "Selena Chandler",
                      "language": "de"
                    }
                  }
                }
                                """.trimIndent()
                            )
                        }

                    response.status shouldBe HttpStatusCode.BadRequest
                }

                test("Should create a document and return correct response") {
                    val response =
                        client
                            .post(DOCUMENTS_PATH) {
                                contentType(ContentType.Application.Json)
                                accept(ContentType.Application.Json)
                                header(HttpHeaders.Authorization, "Bearer maskinporten")
                                header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                                setBody(
                                    JsonApiCreateDocumentRequest(
                                        data = JsonApiRequestResourceObjectWithMeta(
                                            type = "AuthorizationDocument",
                                            attributes = CreateDocumentRequestAttributes(
                                                documentType = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson
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
                                )
                            }

                    response.status shouldBe HttpStatusCode.Created
                    val createDocumentResponse: CreateDocumentResponse = response.body()
                    createDocumentResponse.data.apply {
                        type shouldBe "AuthorizationDocument"
                        id!!.shouldBeValidUuid()
                        attributes.shouldNotBeNull().apply {
                            documentType shouldBe AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson.name
                            status shouldBe AuthorizationDocument.Status.Pending.name
                            validTo shouldBe "${defaultValidTo()}T00:00:00+01:00"

                            val createdAt = OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            val updatedAt = OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            val validTo = Instant.parse(validTo).toLocalDateTime(TimeZone.of("+01:00")).date

                            assertTrue(validTo == defaultValidTo())
                            assertTrue(Duration.between(createdAt, currentTimeWithTimeZone()).abs() < nowTolerance)
                            assertTrue(Duration.between(updatedAt, currentTimeWithTimeZone()).abs() < nowTolerance)
                        }
                        relationships.shouldNotBeNull().apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "0107000000021"
                                    type shouldBe "OrganizationEntity"
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
                            values["requestedFromName"] shouldBe "Hillary Orr"
                            values["requestedForMeteringPointId"] shouldBe "123456789012345678"
                            values["requestedForMeteringPointAddress"] shouldBe "quaerendum"
                            values["balanceSupplierName"] shouldBe "Jami Wade"
                            values["balanceSupplierContractName"] shouldBe "Selena Chandler"
                            values["language"] shouldBe "nb"
                        }
                        links.self shouldBe "$DOCUMENTS_PATH/$id"
                        links.file shouldBe "$DOCUMENTS_PATH/$id.pdf"
                    }
                    createDocumentResponse.links.shouldNotBeNull().apply {
                        self shouldBe DOCUMENTS_PATH
                    }
                    createDocumentResponse.meta.shouldNotBeNull().apply {
                        "createdAt".shouldNotBeNull()
                    }
                    createdDocumentId = createDocumentResponse.data.id.toString()
                    linkToDocument = createDocumentResponse.data.links.self
                    linkToDocumentFile = createDocumentResponse.data.links.file
                }

                test("Get created document should return correct response when authorized party is requestedBy and requestedFrom") {
                    val response = client.get(linkToDocument) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        accept(ContentType.Application.Pdf)
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val getDocumentResponse: GetDocumentSingleResponse = response.body()
                    getDocumentResponse
                        .data.apply {
                            type shouldBe "AuthorizationDocument"
                            id!!.shouldBeValidUuid()
                            attributes.shouldNotBeNull().apply {
                                status shouldBe AuthorizationDocument.Status.Pending.toString()
                                documentType shouldBe AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson.name
                                validTo shouldBe "${defaultValidTo()}T00:00:00+01:00"
                                val createdAt = OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                val updatedAt = OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                val validTo = Instant.parse(validTo).toLocalDateTime(TimeZone.of("+01:00")).date

                                assertTrue(validTo == defaultValidTo())
                                assertTrue(Duration.between(createdAt, currentTimeWithTimeZone()) < nowTolerance)
                                assertTrue(Duration.between(updatedAt, currentTimeWithTimeZone()) < nowTolerance)
                            }
                            relationships.apply {
                                requestedBy.data.apply {
                                    type shouldBe "OrganizationEntity"
                                    id.shouldNotBeNull()
                                }
                                requestedFrom.data.apply {
                                    type shouldBe "Person"
                                    id.shouldNotBeNull()
                                    requestedFromId = id
                                }
                                requestedTo.data.apply {
                                    type shouldBe "Person"
                                    id.shouldNotBeNull()
                                    requestedToId = id
                                }
                                signedBy.shouldBeNull()
                                authorizationGrant.shouldBeNull()
                            }
                            meta.shouldNotBeNull().apply {
                                values["requestedFromName"] shouldBe "Hillary Orr"
                                values["requestedForMeteringPointId"] shouldBe "123456789012345678"
                                values["requestedForMeteringPointAddress"] shouldBe "quaerendum"
                                values["balanceSupplierName"] shouldBe "Jami Wade"
                                values["balanceSupplierContractName"] shouldBe "Selena Chandler"
                            }
                            links.self shouldBe "$DOCUMENTS_PATH/$id"
                            links.file shouldBe "$DOCUMENTS_PATH/$id.pdf"
                        }

                    expectedSignatory = getDocumentResponse.data.relationships.requestedTo.data.id

                    getDocumentResponse.links.shouldNotBeNull().apply {
                        self shouldBe DOCUMENTS_PATH
                    }

                    // Verify that response is the same for authorized enduser
                    pdpContainer.registerEnduserMapping(
                        token = "enduser",
                        partyId = requestedFromId
                    )

                    val enduserResponse = client.get(linkToDocument) {
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        accept(ContentType.Application.Pdf)
                    }

                    enduserResponse.status shouldBe HttpStatusCode.OK
                    val enduserDocumentResponse: GetDocumentSingleResponse = enduserResponse.body()
                    enduserDocumentResponse == getDocumentResponse
                }

                test("Get created document should return 403 Not Authorized when authorized party is requestedTo") {
                    pdpContainer.registerEnduserMapping(
                        token = "not-authorized",
                        partyId = requestedToId
                    )

                    val response = client.get(linkToDocument) {
                        header(HttpHeaders.Authorization, "Bearer not-authorized")
                    }

                    response.status shouldBe HttpStatusCode.Forbidden

                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe HttpStatusCode.Forbidden.value.toString()
                            title shouldBe "Party not authorized"
                            detail shouldBe "The party is not allowed to access this resource"
                        }
                    }
                    responseJson.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Get document list should give proper size given the authorized user") {

                    val requestedByResponse: GetDocumentCollectionResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedByResponse.data.size shouldBe 1

                    val requestedFromResponse: GetDocumentCollectionResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedFromResponse.data.size shouldBe 1

                    val requestedToResponse: GetDocumentCollectionResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer not-authorized")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedToResponse.data.size shouldBe 0
                }

                test("Get pdf file should have proper signature") {
                    signedFile = client.get(linkToDocumentFile) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        accept(ContentType.Application.Pdf)
                    }.bodyAsBytes()
                    signedFile.validateFileIsSignedByUs()
                }

                test("Put signed file should return 204") {
                    val documentSignedByPerson = EndUserSignatureTestHelper().sign(
                        pdfBytes = signedFile,
                        nationalIdentityNumber = REQUESTED_TO_NIN
                    )

                    val response = client.put("$DOCUMENTS_PATH/$createdDocumentId.pdf") {
                        contentType(ContentType.Application.Pdf)
                        setBody(documentSignedByPerson)
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        accept(ContentType.Application.Pdf)
                    }
                    response.status shouldBe HttpStatusCode.NoContent
                    response.bodyAsText().shouldBeEmpty()
                }

                test("Get document should give status Signed and reference to created grant") {
                    val response = client.get(linkToDocument) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val getDocumentResponse: GetDocumentSingleResponse = response.body()
                    getDocumentResponse.data.attributes.shouldNotBeNull().apply {
                        status shouldBe AuthorizationDocument.Status.Signed.toString()
                    }

                    val grantRelationship = getDocumentResponse.data.relationships.authorizationGrant.shouldNotBeNull()
                    grantId = grantRelationship.data.id
                    grantRelationship.apply {
                        data.apply {
                            type shouldBe "AuthorizationGrant"
                            id shouldBe grantId
                        }
                        links.shouldNotBeNull().apply {
                            self shouldBe "${GRANTS_PATH}/$grantId"
                        }
                    }

                    val signedByRelationship = getDocumentResponse.data.relationships.signedBy.shouldNotBeNull()
                    signedByRelationship.apply {
                        data.apply {
                            type shouldBe "Person"
                            id shouldBe expectedSignatory
                        }
                    }
                }

                test("Get grant by id should return proper response") {
                    val response = client.get("$GRANTS_PATH/$grantId") {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: SingleGrantResponse = response.body()
                    responseJson.data.apply {
                        id.shouldBeValidUuid()
                        type shouldBe "AuthorizationGrant"
                        attributes.shouldNotBeNull().apply {
                            status shouldBe "Active"
                            Instant.parse(grantedAt)
                                .toLocalDateTime(TimeZone.of("Europe/Oslo")).date shouldBe Clock.System.now()
                                .toLocalDateTime(TimeZone.UTC).date
                            Instant.parse(validFrom)
                                .toLocalDateTime(TimeZone.of("Europe/Oslo")).date shouldBe Clock.System.now()
                                .toLocalDateTime(TimeZone.UTC).date
                            Instant.parse(validTo)
                                .toLocalDateTime(TimeZone.of("Europe/Oslo")).date shouldBe Clock.System.now()
                                .toLocalDateTime(TimeZone.UTC).date.plus(1, DateTimeUnit.YEAR)
                        }
                        relationships.apply {
                            grantedFor.apply {
                                data.apply {
                                    id.shouldNotBeNull()
                                    type shouldBe "Person"
                                }
                            }
                            grantedBy.apply {
                                data.apply {
                                    id.shouldNotBeNull()
                                    type shouldBe "Person"
                                }
                            }
                            grantedTo.apply {
                                data.apply {
                                    id.shouldNotBeNull()
                                    type shouldBe "OrganizationEntity"
                                }
                            }
                            source.apply {
                                data.apply {
                                    id shouldBe createdDocumentId
                                    type shouldBe "AuthorizationDocument"
                                }
                                links.shouldNotBeNull().apply {
                                    self shouldBe "$DOCUMENTS_PATH/$createdDocumentId"
                                }
                            }
                        }
                    }
                }

                test("Get grant scopes by id should return proper response") {
                    val response = client.get("$GRANTS_PATH/$grantId/scopes") {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: AuthorizationGrantScopesResponse = response.body()
                    responseJson.data.apply {
                        size shouldBe 1
                        this[0].apply {
                            id.shouldBeValidUuid()
                            type shouldBe "AuthorizationScope"
                            attributes.shouldNotBeNull().apply {
                                permissionType shouldBe AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson
                            }
                            relationships.shouldNotBeNull().apply {
                                authorizedResources.apply {
                                    data.size shouldBe 1
                                    data[0].apply {
                                        id shouldBe "123456789012345678"
                                        type shouldBe AuthorizationScope.AuthorizationResource.MeteringPoint.name
                                    }
                                }
                            }
                        }
                        responseJson.meta.shouldNotBeNull().apply {
                            get("createdAt").shouldNotBeNull()
                        }
                        responseJson.links.shouldNotBeNull().apply {
                            self shouldBe "$GRANTS_PATH/$grantId/scopes"
                        }
                    }
                }
            }
        }

        context("Document create language handling in isolated flow") {
            testApplication {
                client = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                application {
                    applicationModule()
                    testBusinessProcessesModule()
                    commonModule()
                    grantsModule()
                    module()
                }

                environment {
                    config = MapApplicationConfig(
                        "ktor.database.username" to "app",
                        "ktor.database.password" to "app",
                        "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
                        "ktor.database.driverClass" to "org.postgresql.Driver",
                        "pdfGenerator.mustacheResourcePath" to "templates",
                        "pdfGenerator.useTestPdfNotice" to "true",
                        "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
                        "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
                        "pdfSigner.vault.key" to "test-key",
                        "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.bankIdIdRoot" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.bankIdRootDir" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATES_DIR,
                        "featureToggle.enableEndpoints" to "true",
                        "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
                        "pdp.baseUrl" to "http://localhost:8085"
                    )
                }

                test("Should create document with explicit language in meta") {
                    val response =
                        client
                            .post(DOCUMENTS_PATH) {
                                contentType(ContentType.Application.Json)
                                accept(ContentType.Application.Json)
                                header(HttpHeaders.Authorization, "Bearer maskinporten")
                                header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                                setBody(
                                    JsonApiCreateDocumentRequest(
                                        data = JsonApiRequestResourceObjectWithMeta(
                                            type = "AuthorizationDocument",
                                            attributes = CreateDocumentRequestAttributes(
                                                documentType = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson
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
                                                balanceSupplierContractName = "Selena Chandler",
                                                language = SupportedLanguageDTO.EN,
                                            )
                                        )
                                    )
                                )
                            }

                    response.status shouldBe HttpStatusCode.Created
                    val createDocumentResponse: CreateDocumentResponse = response.body()
                    createDocumentResponse.data.meta.shouldNotBeNull().apply {
                        values["language"] shouldBe "en"
                    }
                }
            }
        }

        // TODO this should be moved to a proper place, but for that, we need to revisit the test setup
        context("Invalid User-Agent header") {
            testApplication {
                client = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                application {
                    applicationModule()
                    testBusinessProcessesModule()
                    commonModule()
                    grantsModule()
                    module()
                }

                environment {
                    config = MapApplicationConfig(
                        "ktor.database.username" to "app",
                        "ktor.database.password" to "app",
                        "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
                        "ktor.database.driverClass" to "org.postgresql.Driver",
                        "pdfGenerator.mustacheResourcePath" to "templates",
                        "pdfGenerator.useTestPdfNotice" to "true",
                        "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
                        "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
                        "pdfSigner.vault.key" to "test-key",
                        "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.bankIdRootDir" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATES_DIR,
                        "featureToggle.enableEndpoints" to "true",
                        "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
                        "pdp.baseUrl" to "http://localhost:8085"
                    )
                }

                test("Empty User-Agent header should return 400") {
                    val response =
                        client
                            .get(DOCUMENTS_PATH) {
                                header(HttpHeaders.Authorization, "Bearer maskinporten")
                                header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                                header(HttpHeaders.UserAgent, "")
                            }

                    response.status shouldBe HttpStatusCode.BadRequest
                    val error: JsonApiErrorCollection = response.body()
                    error.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "400"
                            title shouldBe "Bad request"
                            detail shouldBe "Missing User-Agent header"
                        }
                    }
                    error.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }
        }
    })

private const val REQUESTED_FROM_NIN = "02916297702"
private const val REQUESTED_TO_NIN = "14810797496"

private class TestDocumentBusinessHandler : DocumentBusinessHandler {
    override suspend fun validateAndReturnDocumentCommand(
        model: CreateDocumentModel
    ) = when (model.documentType) {
        AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson -> {
            val meta = model.meta
            DocumentCommand(
                type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
                requestedFrom = meta.requestedFrom,
                requestedTo = meta.requestedTo,
                requestedBy = meta.requestedBy,
                validTo = defaultValidTo().toTimeZoneOffsetDateTimeAtStartOfDay(),
                scopes = listOf(
                    CreateScopeData(
                        authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                        authorizedResourceId = meta.requestedForMeteringPointId,
                        permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson
                    )
                ),
                meta = ChangeOfBalanceSupplierBusinessMeta(
                    language = meta.language.toSupportedLanguage(),
                    requestedFromName = meta.requestedFromName,
                    requestedForMeteringPointId = meta.requestedForMeteringPointId,
                    requestedForMeterNumber = "123456789",
                    requestedForMeteringPointAddress = meta.requestedForMeteringPointAddress,
                    balanceSupplierName = meta.balanceSupplierName,
                    balanceSupplierContractName = meta.balanceSupplierContractName
                )
            ).right()
        }

        else -> BusinessProcessError.Validation(detail = "Unsupported document type").left()
    }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        CreateGrantProperties(
            validFrom = today(),
            validTo = defaultValidTo()
        )
}

fun Application.testBusinessProcessesModule() {
    koinModule {
        singleOf(::TestDocumentBusinessHandler) bind DocumentBusinessHandler::class
    }
}
