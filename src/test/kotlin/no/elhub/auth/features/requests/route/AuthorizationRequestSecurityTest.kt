package no.elhub.auth.features.requests.route

import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.module
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.common.AuthorizationRequestPropertyTable
import no.elhub.auth.features.requests.common.AuthorizationRequestTable
import no.elhub.auth.features.requests.common.DatabaseRequestStatus
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.dto.CreateRequestAttributes
import no.elhub.auth.features.requests.create.dto.CreateRequestMeta
import no.elhub.auth.features.requests.create.dto.CreateRequestResponse
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.model.defaultRequestValidTo
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import no.elhub.auth.features.requests.get.dto.GetRequestSingleResponse
import no.elhub.auth.features.requests.query.dto.GetRequestCollectionResponse
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.UpdateRequestAttributes
import no.elhub.auth.features.requests.update.dto.UpdateRequestResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.ktor.plugin.koinModule
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.time.LocalDate as JavaLocalDate
import no.elhub.auth.module as applicationModule

class AuthorizationRequestSecurityTest : FunSpec({
    val pdpContainer = PdpTestContainerExtension()
    extensions(
        AuthPersonsTestContainerExtension,
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        //RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        //RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"),
        //RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),
        //pdpContainer
    )

    beforeSpec {
        pdpContainer.registerMaskinportenMapping(
            token = "maskinporten",
            actingFunction = "BalanceSupplier",
            actingGln = "0107000000021"
        )
        pdpContainer.registerEnduserMapping(
            token = "enduser",
            partyId = "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
        )
        pdpContainer.registerMaskinportenMapping(
            token = "gridowner",
            actingFunction = "GridOwner",
            actingGln = "0107000000038"
        )
        pdpContainer.registerInvalidTokenMapping()
    }

    context("Some context") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("Should return 401 Unauthorized when requestee has no token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.Unauthorized
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "401"
                        title shouldBe "Missing authorization"
                        detail shouldBe "Bearer token is required in the Authorization header."
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }

            test("Should return 401 Unauthorized when requestee has invalid token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }
                response.status shouldBe HttpStatusCode.Unauthorized
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "401"
                        title shouldBe "Invalid token"
                        detail shouldBe "Token could not be verified."
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }

            test("Should return 403 Forbidden when requestee has valid gridowner token") {

                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Kasper Lind",
                        "requestedForMeteringPointId" to "1234567890555",
                        "requestedForMeteringPointAddress" to "Example Street 2, 0654 Oslo",
                        "balanceSupplierName" to "Power AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val patchResponse =
                    client.patch("${REQUESTS_PATH}/$requestId") {
                        header(HttpHeaders.Authorization, "Bearer gridowner")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000038")
                        contentType(ContentType.Application.Json)
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    type = "AuthorizationRequest",
                                    attributes = UpdateRequestAttributes(
                                        status = AuthorizationRequest.Status.Accepted
                                    )
                                )
                            ),
                        )
                    }

                patchResponse.status shouldBe HttpStatusCode.Forbidden

                val responseJson: JsonApiErrorCollection = patchResponse.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "403"
                        title shouldBe "Unsupported party type"
                        detail shouldBe "The party type you are authorized as is not supported for this endpoint."
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
        }

    }
})

