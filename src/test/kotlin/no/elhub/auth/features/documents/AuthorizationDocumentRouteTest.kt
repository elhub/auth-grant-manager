package no.elhub.auth.features.documents

import io.kotest.assertions.throwables.shouldNotThrowAny
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
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DateTimeUnit.TimeBased
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.businessprocesses.changeofsupplier.defaultValidTo
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.documents.create.dto.CreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponse
import no.elhub.auth.features.documents.query.dto.GetDocumentCollectionResponse
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.grants.common.dto.AuthorizationGrantScopesResponse
import no.elhub.auth.features.grants.common.dto.SingleGrantResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
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
                actingFunction = "BalanceSupplier"
            )
            AuthPersonsTestContainer.registerPersonMapping(
                nin = REQUESTED_FROM_NIN,
                personId = REQUESTED_FROM_ID
            )
            AuthPersonsTestContainer.registerPersonMapping(
                nin = REQUESTED_TO_NIN,
                personId = REQUESTED_TO_ID
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
                        "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
                        "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
                        "pdfSigner.vault.key" to "test-key",
                        "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.bankIdIdRoot" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATE_LOCATION,
                        "featureToggle.enableEndpoints" to "true",
                        "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
                        "pdp.baseUrl" to "http://localhost:8085"
                    )
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
                                                documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation
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
                        id.shouldNotBeNull()
                        attributes.shouldNotBeNull().apply {
                            documentType shouldBe AuthorizationDocument.Type.ChangeOfSupplierConfirmation.name
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
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val getDocumentResponse: GetDocumentSingleResponse = response.body()
                    getDocumentResponse
                        .data.apply {
                            type shouldBe "AuthorizationDocument"
                            id.shouldNotBeNull()
                            attributes.shouldNotBeNull().apply {
                                status shouldBe AuthorizationDocument.Status.Pending.toString()
                                documentType shouldBe AuthorizationDocument.Type.ChangeOfSupplierConfirmation.name
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
                            code shouldBe "not_authorized"
                            title shouldBe "Party not authorized"
                            detail shouldBe "The party is not allowed to access this resource"
                        }
                    }
                }

                test("Get document list should give proper size given the authorized user") {

                    // When authorized party is the requestedBy number of documents returned should be 1
                    val requestedByResponse: GetDocumentCollectionResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedByResponse.data.size shouldBe 1

                    // When authorized party is the requestedFrom number of documents returned should be 1
                    val requestedFromResponse: GetDocumentCollectionResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedFromResponse.data.size shouldBe 1

                    // When authorized party is the requestedTo number of documents returned should be 0
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
                        id.shouldNotBeNull()
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
                            id shouldBe "1"
                            type shouldBe "AuthorizationScope"
                            attributes.shouldNotBeNull().apply {
                                permissionType shouldBe AuthorizationScope.PermissionType.ChangeOfSupplier
                            }
                            relationships.shouldNotBeNull().apply {
                                authorizedResources.apply {
                                    data.size shouldBe 1
                                    data[0].apply {
                                        id shouldBe "123456789012345678"
                                        type shouldBe AuthorizationScope.ElhubResource.MeteringPoint.name
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
                        "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
                        "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
                        "pdfSigner.vault.key" to "test-key",
                        "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                        "pdfSigner.certificate.bankIdIdRoot" to TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATE_LOCATION,
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
                            code shouldBe "bad_request"
                            title shouldBe "Bad request"
                            detail shouldBe "Missing User-Agent header"
                        }
                    }
                }
            }
        }
    })

private const val REQUESTED_FROM_NIN = "98765432109"
private const val REQUESTED_TO_NIN = "00011122233"
private val REQUESTED_FROM_ID = UUID.fromString("5c9f5b1c-7a01-4d8d-9f27-9de7479adf52")
private val REQUESTED_TO_ID = UUID.fromString("d6fe3b43-0d6b-4e7c-8bd1-12a2ed05a5f6")
