package no.elhub.auth.features.documents

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldContainKey
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
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.documents.create.dto.CreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.features.documents.get.GetDocumentResponse
import no.elhub.auth.features.documents.query.AuthorizationDocumentsResponse
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.grants.common.dto.AuthorizationGrantScopesResponse
import no.elhub.auth.features.grants.common.dto.SingleGrantResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
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
        }

        lateinit var createdDocumentId: String
        lateinit var expectedSignatory: String
        lateinit var linkToDocument: String
        lateinit var linkToDocumentFile: String
        lateinit var signedFile: ByteArray
        lateinit var grantId: String
        lateinit var requestedFromId: String
        lateinit var requestedToId: String

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
                                                    idValue = "98765432109"
                                                ),
                                                requestedTo = PartyIdentifier(
                                                    idType = PartyIdentifierType.NationalIdentityNumber,
                                                    idValue = "00011122233"
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
                            documentType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe AuthorizationDocument.Status.Pending.name
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
                            val createdAt = values["createdAt"].shouldNotBeNull()
                            val updatedAt = values["updatedAt"].shouldNotBeNull()

                            shouldNotThrowAny {
                                OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            }

                            values["requestedFromName"] shouldBe "Hillary Orr"
                            values["requestedForMeteringPointId"] shouldBe "123456789012345678"
                            values["requestedForMeteringPointAddress"] shouldBe "quaerendum"
                            values["balanceSupplierName"] shouldBe "Jami Wade"
                            values["balanceSupplierContractName"] shouldBe "Selena Chandler"
                        }
                        links.self shouldBe "$DOCUMENTS_PATH/$id"
                        links.file shouldBe "$DOCUMENTS_PATH/$id.pdf"
                    }
                    createDocumentResponse.links.apply {
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
                    val getDocumentResponse: GetDocumentResponse = response.body()
                    getDocumentResponse
                        .data.apply {
                            type shouldBe "AuthorizationDocument"
                            id.shouldNotBeNull()
                            attributes.shouldNotBeNull().apply {
                                status shouldBe AuthorizationDocument.Status.Pending.toString()
                                createdAt.shouldNotBeNull()
                                updatedAt.shouldNotBeNull()
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
                                grant.shouldBeNull()
                            }
                            meta.shouldNotBeNull().toMap().apply {
                                this.mapValues { (_, v) ->
                                    v.jsonPrimitive.content
                                }.apply {
                                    shouldContain("requestedFromName", "Hillary Orr")
                                    shouldContain("requestedForMeteringPointId", "123456789012345678")
                                    shouldContain("requestedForMeteringPointAddress", "quaerendum")
                                    shouldContain("balanceSupplierName", "Jami Wade")
                                    shouldContain("balanceSupplierContractName", "Selena Chandler")
                                    shouldContainKey("createdAt")
                                    shouldContainKey("updatedAt")
                                }
                            }
                        }

                    expectedSignatory = getDocumentResponse.data.relationships.requestedTo.data.id

                    getDocumentResponse.links.apply {
                        self shouldBe "$DOCUMENTS_PATH/$createdDocumentId"
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
                    val enduserDocumentResponse: GetDocumentResponse = enduserResponse.body()
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

                    val jsonApiError: JsonApiErrorCollection = response.body()

                    jsonApiError shouldBe JsonApiErrorCollection(
                        listOf(
                            JsonApiErrorObject(
                                status = HttpStatusCode.Forbidden.value.toString(),
                                code = "NOT_AUTHORIZED",
                                title = "Party Not Authorized",
                                detail = "The party is not allowed to access this resource",
                            )
                        )
                    )
                }

                test("Get document list should give proper size given the authorized user") {

                    // When authorized party is the requestedBy number of documents returned should be 1
                    val requestedByResponse: AuthorizationDocumentsResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedByResponse.size shouldBe 1

                    // When authorized party is the requestedFrom number of documents returned should be 1
                    val requestedFromResponse: AuthorizationDocumentsResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedFromResponse.size shouldBe 1

                    // When authorized party is the requestedTo number of documents returned should be 0
                    val requestedToResponse: AuthorizationDocumentsResponse = client.get(DOCUMENTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer not-authorized")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.body()

                    requestedToResponse.size shouldBe 0
                }

                test("Get pdf file should have proper signature") {
                    signedFile = client.get(linkToDocumentFile) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    }.bodyAsBytes()
                    signedFile.validateFileIsSignedByUs()
                }

                test("Put signed file should return 204") {
                    val response = client.put("$DOCUMENTS_PATH/$createdDocumentId.pdf") {
                        contentType(ContentType.Application.Pdf)
                        setBody(signedFile)
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
                    val getDocumentResponse: GetDocumentResponse = response.body()
                    getDocumentResponse.data.attributes.shouldNotBeNull().apply {
                        status shouldBe AuthorizationDocument.Status.Signed.toString()
                    }

                    val grantRelationship = getDocumentResponse.data.relationships.grant.shouldNotBeNull()
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
                            OffsetDateTime.parse(grantedAt).toLocalDate() shouldBe LocalDate.now()
                            OffsetDateTime.parse(validFrom).toLocalDate() shouldBe LocalDate.now()
                            OffsetDateTime.parse(validTo).toLocalDate() shouldBe LocalDate.now().plusYears(1)
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
                        responseJson.links.apply {
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

                    error.errors shouldHaveSize 1
                    error.errors[0].apply {
                        status shouldBe "400"
                        detail shouldBe "Missing User-Agent header"
                    }
                }
            }
        }
    })
