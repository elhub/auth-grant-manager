package no.elhub.auth.features.documents

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.common.PartyIdentifierType
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.documents.confirm.ConfirmDocumentResponse
import no.elhub.auth.features.documents.create.CreateDocumentResponse
import no.elhub.auth.features.documents.create.DocumentMeta
import no.elhub.auth.features.documents.create.DocumentRequestAttributes
import no.elhub.auth.features.documents.create.Request
import no.elhub.auth.features.documents.get.GetDocumentResponse
import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.grants.PermissionType
import no.elhub.auth.features.grants.common.AuthorizationGrantResponse
import no.elhub.auth.features.grants.common.AuthorizationGrantScopesResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import java.time.LocalDate
import java.time.LocalDateTime
import no.elhub.auth.features.grants.module as grantsModule
import no.elhub.auth.module as applicationModule

class AuthorizationDocumentRouteTest :
    FunSpec({
        extensions(
            PostgresTestContainerExtension(),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
            VaultTransitTestContainerExtension,
            AuthPersonsTestContainerExtension,
        )

        lateinit var createdDocumentId: String
        lateinit var linkToDocument: String
        lateinit var linkToDocumentFile: String
        lateinit var signedFile: ByteArray
        lateinit var grantId: String

        context("Run document happy flow") {
            testApplication {
                client = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                application {
                    applicationModule()
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
                        "authPersons.baseUri" to AuthPersonsTestContainer.baseUri()
                    )
                }

                test("Should create a document and return correct response") {
                    val response =
                        client
                            .post(DOCUMENTS_PATH) {
                                contentType(ContentType.Application.Json)
                                accept(ContentType.Application.Json)
                                setBody(
                                    Request(
                                        data = JsonApiRequestResourceObjectWithMeta(
                                            type = "AuthorizationDocument",
                                            attributes = DocumentRequestAttributes(
                                                documentType = AuthorizationDocument.Type.ChangeOfSupplierConfirmation
                                            ),
                                            meta = DocumentMeta(
                                                requestedBy = PartyIdentifier(
                                                    idType = PartyIdentifierType.NationalIdentityNumber,
                                                    idValue = "12345678901"
                                                ),
                                                requestedFrom = PartyIdentifier(
                                                    idType = PartyIdentifierType.NationalIdentityNumber,
                                                    idValue = "98765432109"
                                                ),
                                                requestedTo = PartyIdentifier(
                                                    idType = PartyIdentifierType.NationalIdentityNumber,
                                                    idValue = "00011122233"
                                                ),
                                                signedBy = PartyIdentifier(
                                                    idType = PartyIdentifierType.NationalIdentityNumber,
                                                    idValue = "98765432100"
                                                ),
                                                requestedFromName = "Hillary Orr",
                                                requestedForMeteringPointId = "atomorum",
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
                        links.self shouldBe "$DOCUMENTS_PATH/$id"
                        links.file shouldBe "$DOCUMENTS_PATH/$id.pdf"
                    }

                    createDocumentResponse.links.apply {
                        self shouldBe "/authorization-documents"
                    }

                    createdDocumentId = createDocumentResponse.data.id
                    linkToDocument = createDocumentResponse.data.links.self
                    linkToDocumentFile = createDocumentResponse.data.links.file
                }

                test("Get created document should return correct response") {
                    val response = client.get(linkToDocument)
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
                                    type shouldBe "Person"
                                    id.shouldNotBeNull()
                                }
                                requestedFrom.data.apply {
                                    type shouldBe "Person"
                                    id.shouldNotBeNull()
                                }
                            }
                        }

                    getDocumentResponse.links.apply {
                        self shouldBe "/authorization-documents/$createdDocumentId"
                    }
                }

                test("Get pdf file should have proper signature") {
                    signedFile = client.get(linkToDocumentFile).bodyAsBytes()
                    signedFile.validateFileIsSignedByUs()
                }

                test("Patch signed file should return correct response including reference to grant") {
                    val response = client.patch("$DOCUMENTS_PATH/$createdDocumentId.pdf") {
                        contentType(ContentType.Application.Pdf)
                        setBody(signedFile)
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val patchDocumentResponse: ConfirmDocumentResponse = response.body()
                    patchDocumentResponse.data.apply {
                        type shouldBe "AuthorizationDocument"
                        id shouldBe createdDocumentId
                        attributes.shouldNotBeNull().apply {
                            status shouldBe AuthorizationDocument.Status.Signed.toString()
                            documentType shouldBe AuthorizationDocument.Type.ChangeOfSupplierConfirmation.toString()
                        }
                        relationships.apply {
                            grant.apply {
                                data.id.shouldNotBeNull()
                                data.type shouldBe "AuthorizationGrant"
                            }
                        }
                    }
                    grantId = patchDocumentResponse.data.relationships.grant.data.id
                }

                test("Get document should give status Signed") {
                    val response = client.get(linkToDocument)
                    response.status shouldBe HttpStatusCode.OK
                    val getDocumentResponse: GetDocumentResponse = response.body()
                    getDocumentResponse.data.attributes.shouldNotBeNull().apply {
                        status shouldBe AuthorizationDocument.Status.Signed.toString()
                    }
                }

                test("Get grant by id should return proper response") {
                    val response = client.get("$GRANTS_PATH/$grantId")
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: AuthorizationGrantResponse = response.body()
                    responseJson.data.apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationGrant"
                        attributes.shouldNotBeNull().apply {
                            status shouldBe "Active"
                            grantedAt.shouldNotBeNull()
                            LocalDateTime.parse(validFrom).toLocalDate() shouldBe LocalDate.now()
                            LocalDateTime.parse(validTo).toLocalDate() shouldBe LocalDate.now().plusYears(1)
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
                                    type shouldBe "Person"
                                }
                            }
                        }
                    }
                }
                test("Get grant scopes by id should return proper response") {
                    val response = client.get("$GRANTS_PATH/$grantId/scopes")
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: AuthorizationGrantScopesResponse = response.body()
                    responseJson.data.apply {
                        size shouldBe 1
                        this[0].apply {
                            id shouldBe "1"
                            type shouldBe "AuthorizationScope"
                            attributes.shouldNotBeNull()
                            attributes!!.apply {
                                authorizedResourceType shouldBe ElhubResource.MeteringPoint
                                authorizedResourceId shouldBe "Something"
                                permissionType shouldBe PermissionType.ChangeOfSupplier
                                createdAt.shouldNotBeNull()
                            }
                        }
                    }
                }
            }
        }
    })
