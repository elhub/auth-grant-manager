package no.elhub.auth.features.documents

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.netty.EngineMain
import io.ktor.server.testing.TestApplication
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.defaultTestApplication
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.documents.common.AuthorizationDocumentResponse
import no.elhub.auth.features.documents.create.CreateDocumentRequest
import no.elhub.auth.features.documents.create.DocumentMeta
import no.elhub.auth.features.documents.create.DocumentRelationships
import no.elhub.auth.features.documents.create.DocumentRequestAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.request.JsonApiRequest
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithRelationships
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithRelationshipsAndMeta
import no.elhub.auth.module as applicationModule

class AuthorizationDocumentRouteTest :
    FunSpec({
        extensions(
            PostgresTestContainerExtension,
            VaultTransitTestContainerExtension
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
                    )
                }

                test("Should create a document with a valid signature") {
                    val response =
                        client
                            .post(DOCUMENTS_PATH) {
                                contentType(ContentType.Application.Json)
                                accept(ContentType.Application.Json)
                                setBody(
                                    CreateDocumentRequest(
                                        data =
                                            JsonApiRequestResourceObjectWithRelationshipsAndMeta<DocumentRequestAttributes, DocumentRelationships, DocumentMeta>(
                                                "AuthorizationDocument",
                                                attributes = DocumentRequestAttributes(
                                                    AuthorizationDocument.Type.ChangeOfSupplierConfirmation
                                                ),
                                                relationships = DocumentRelationships(
                                                    requestedBy = JsonApiRelationshipToOne(
                                                        JsonApiRelationshipData(
                                                            "User",
                                                            "12345678901"
                                                        )
                                                    ),
                                                    requestedFrom = JsonApiRelationshipToOne(
                                                        JsonApiRelationshipData(
                                                            "User",
                                                            "98765432109"
                                                        )
                                                    )
                                                ),
                                                meta = DocumentMeta(
                                                    "Some user",
                                                    "1234",
                                                    "Adressevegen 1, 1234 Oslo",
                                                    "Supplier AS",
                                                    "My Contract",
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
                                    id shouldBe "12345678901"
                                    type shouldBe "User"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "98765432109"
                                    type shouldBe "User"
                                }
                            }
                        }
                    }

                    // Get the pdf to validate signature
                    val document = client.get("$DOCUMENTS_PATH/${documentResponse.data.id}.pdf")
                    DocumentValidationHelper.validateInitialDocumentSignature(document.bodyAsBytes())
                }
            }
        }
    })
