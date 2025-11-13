package no.elhub.auth.features.documents

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.documents.common.AuthorizationDocumentResponse
import no.elhub.auth.features.documents.create.DocumentMeta
import no.elhub.auth.features.documents.create.DocumentRequestAttributes
import no.elhub.auth.features.documents.create.PartyIdentifier
import no.elhub.auth.features.documents.create.PartyIdentifierType
import no.elhub.auth.features.documents.create.Request
import no.elhub.auth.features.documents.create.RequestData
import no.elhub.auth.module as applicationModule

class AuthorizationDocumentRouteTest :
    FunSpec({
        extensions(
            PostgresTestContainerExtension,
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
            VaultTransitTestContainerExtension,
            AuthPersonsTestContainerExtension,
        )

        context("Create document") {
            testApplication {
                client = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                application {
                    applicationModule()
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

                test("Should create a document with a valid signature") {
                    val response =
                        client
                            .post(DOCUMENTS_PATH) {
                                contentType(ContentType.Application.Json)
                                accept(ContentType.Application.Json)
                                setBody(
                                    Request(
                                        data =
                                        RequestData(
                                            "AuthorizationDocument",
                                            attributes = DocumentRequestAttributes(
                                                AuthorizationDocument.Type.ChangeOfSupplierConfirmation
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
                                                    idValue = "something"
                                                ),
                                                signedBy = PartyIdentifier(
                                                    idType = PartyIdentifierType.NationalIdentityNumber,
                                                    idValue = "something"
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
                    val documentResponse: AuthorizationDocumentResponse = response.body()
                    documentResponse.data.apply {
                        type shouldBe "AuthorizationDocument"
                        id.shouldNotBeNull()
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            status shouldBe "Pending"
                        }
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldNotBe null
                                    type shouldBe "Person"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldNotBe null
                                    type shouldBe "Person"
                                }
                            }
                        }
                    }

                    // Get the pdf to validate signature
                    val file = client.get("$DOCUMENTS_PATH/${documentResponse.data.id}.pdf").bodyAsBytes()
                    file.validateFileIsSignedByUs()
                }
            }
        }
    })
