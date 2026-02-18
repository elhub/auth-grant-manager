package no.elhub.auth.features.documents.route

import io.ktor.client.request.header
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
import no.elhub.auth.features.businessprocesses.changeofsupplier.defaultValidTo
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.changeofsupplier.today
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
import no.elhub.auth.features.documents.create.CreateError
import no.elhub.auth.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.validateFileIsSignedByUs
import no.elhub.auth.validateInvalidTokenResponse
import no.elhub.auth.validateMissingTokenResponse
import no.elhub.auth.validateUnsupportedPartyResponse
import no.elhub.auth.features.documents.EndUserSignatureTestHelper
import no.elhub.auth.features.documents.module
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestAttributes
import no.elhub.auth.features.documents.create.dto.CreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
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

class AuthorizationDocumentRouteSecurityTest : FunSpec({
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
        pdpContainer.registerInvalidTokenMapping()
    }

    context("When token is missing") {
        testApplication {
            setUpAuthorizationDocumentsTestApplication()
            test("GET /authorization-documents/ returns 401") {
                val queryResponse = client.get(DOCUMENTS_PATH) {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateMissingTokenResponse(queryResponse)
            }

            test("POST /authorization-documents/ returns 401") {
                val response = client.post(DOCUMENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    setBody(examplePostBody)
                }
                validateMissingTokenResponse(response)
            }
            test("GET and PUT /authorization-documents/[{id}.pdf] return 401") {
                val createResponse = client.post(DOCUMENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    setBody(examplePostBody)
                }

                // GET <...>/{id}.pdf
                val id = createResponse.body<CreateDocumentResponse>().data.id!!
                val fileResponse = client.get("$DOCUMENTS_PATH/$id.pdf") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateMissingTokenResponse(fileResponse)


                // GET <...>/{id}
                val docDataResponse = client.get("$DOCUMENTS_PATH/$id") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateMissingTokenResponse(docDataResponse)

                val signedFile = client.get("$DOCUMENTS_PATH/$id.pdf") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }.bodyAsBytes()
                val documentSignedByPerson = EndUserSignatureTestHelper().sign(
                    pdfBytes = signedFile,
                    nationalIdentityNumber = REQUESTED_TO_NIN
                )

                // PUT <...>/{id}.pdf
                val response = client.put("$DOCUMENTS_PATH/$id.pdf") {
                    contentType(ContentType.Application.Pdf)
                    setBody(documentSignedByPerson)
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateMissingTokenResponse(response)
            }
        }

    }
    context("When token is invalid") {
        testApplication {
            setUpAuthorizationDocumentsTestApplication()
            test("GET /authorization-documents/ returns 401") {
                val queryResponse = client.get(DOCUMENTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateInvalidTokenResponse(queryResponse)
            }

            test("POST /authorization-documents/ returns 401") {
                val response = client.post(DOCUMENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    setBody(examplePostBody)
                }
                validateInvalidTokenResponse(response)
            }
            test("GET and PUT /authorization-documents/[{id}.pdf] return 401") {
                val createResponse = client.post(DOCUMENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    setBody(examplePostBody)
                }

                // GET <...>/{id}.pdf
                val id = createResponse.body<CreateDocumentResponse>().data.id!!
                val fileResponse = client.get("$DOCUMENTS_PATH/$id.pdf") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateInvalidTokenResponse(fileResponse)


                // GET <...>/{id}
                val docDataResponse = client.get("$DOCUMENTS_PATH/$id") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateInvalidTokenResponse(docDataResponse)

                val signedFile = client.get("$DOCUMENTS_PATH/$id.pdf") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }.bodyAsBytes()
                val documentSignedByPerson = EndUserSignatureTestHelper().sign(
                    pdfBytes = signedFile,
                    nationalIdentityNumber = REQUESTED_TO_NIN
                )

                // PUT <...>/{id}.pdf
                val response = client.put("$DOCUMENTS_PATH/$id.pdf") {
                    contentType(ContentType.Application.Pdf)
                    setBody(documentSignedByPerson)
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                validateInvalidTokenResponse(response)
            }
        }
    }
    context("Incorrect role or resource ownership") {
        testApplication {
            setUpAuthorizationDocumentsTestApplication()
            test("GET /authorization-documents/ on newly created document should return 403 Not Authorized when authorized party is requestedTo") {
                val createResponse = client.post(DOCUMENTS_PATH) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    setBody(examplePostBody)
                }
                createResponse.status shouldBe HttpStatusCode.Created
                val createDocumentResponse: CreateDocumentResponse = createResponse.body()
                val linkToDocument = createDocumentResponse.data.links.self
                val requestedTo = createDocumentResponse.data.relationships.requestedTo.data.id

                pdpContainer.registerEnduserMapping(
                    token = "not-authorized",
                    partyId = requestedTo
                )

                val getResponse = client.get(linkToDocument) {
                    header(HttpHeaders.Authorization, "Bearer not-authorized")
                }

                getResponse.status shouldBe HttpStatusCode.Forbidden

                val responseJson: JsonApiErrorCollection = getResponse.body()
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
        }
    }

})

