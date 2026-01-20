package no.elhub.auth.features.requests

import arrow.core.left
import arrow.core.right
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
import no.elhub.auth.features.businessprocesses.structuredata.meteringPointsServiceModule
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PdpTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.common.AuthorizationRequestPropertyTable
import no.elhub.auth.features.requests.common.AuthorizationRequestTable
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
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.plugin.koinModule
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.time.LocalDate as JavaLocalDate
import no.elhub.auth.module as applicationModule

class AuthorizationRequestRouteTest : FunSpec({
    val pdpContainer = PdpTestContainerExtension()
    extensions(
        AuthPersonsTestContainerExtension,
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),
        pdpContainer
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

    context("GET /authorization-requests") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("Should return only requests for authenticated organization when using Maskinporten token") {
                val response = client.get(REQUESTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestCollectionResponse = response.body()
                responseJson.data.apply {
                    size shouldBe 1
                    forEach { item ->
                        val validTo = item.attributes.validTo
                        val createdAt = item.attributes.createdAt
                        val updatedAt = item.attributes.updatedAt
                        shouldNotThrowAny {
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                }
            }

            test("Should return only requests for authenticated end user when using end-user token") {
                val response = client.get(REQUESTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }

                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestCollectionResponse = response.body()

                responseJson.data.apply {
                    size shouldBe 4

                    forEach { item ->
                        val validTo = item.attributes.validTo
                        shouldNotThrowAny {
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                        item.relationships.shouldNotBeNull().apply {
                            requestedTo.data.apply {
                                id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                type shouldBe "Person"
                            }
                        }
                    }
                }
            }

            test("Should return empty list when authorized organization has no requests") {
                pdpContainer.registerMaskinportenMapping(
                    token = "no-requests",
                    actingGln = "0107000000022",
                    actingFunction = "BalanceSupplier",
                )

                val response = client.get(REQUESTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer no-requests")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000022")
                }

                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestCollectionResponse = response.body()

                responseJson.data.apply {
                    size shouldBe 0
                }
            }
        }
    }

    context("GET /authorization-requests/{id}") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("Should return the request for authenticated organization when using Maskinporten token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7252c1a40c50") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestSingleResponse = response.body()
                responseJson.data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "AuthorizationRequest"
                    attributes.shouldNotBeNull().apply {
                        requestType shouldBe "ChangeOfSupplierConfirmation"
                        status shouldBe "Accepted"
                        val validTo = validTo.shouldNotBeNull()
                        val createdAt = createdAt.shouldNotBeNull()

                        val updatedAt = updatedAt.shouldNotBeNull()

                        shouldNotThrowAny {
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                    relationships.shouldNotBeNull().apply {
                        requestedBy.apply {
                            data.apply {
                                id shouldBe "987654321"
                                type shouldBe "Organization"
                            }
                        }
                        requestedFrom.apply {
                            data.apply {
                                id shouldBe "0107000000021"
                                type shouldBe "OrganizationEntity"
                            }
                        }
                        requestedTo.apply {
                            data.apply {
                                id shouldBe "0107000000021"
                                type shouldBe "OrganizationEntity"
                            }
                        }
                        approvedBy.shouldNotBeNull().apply {
                            data.apply {
                                id shouldBe "0107000000021"
                                type shouldBe "OrganizationEntity"
                            }
                        }
                        grant.shouldNotBeNull().apply {
                            data.apply {
                                id.shouldNotBeNull()
                                type shouldBe "AuthorizationGrant"
                            }
                            links.shouldNotBeNull()
                        }
                    }
                    links.shouldNotBeNull().apply {
                        self.shouldNotBeNull()
                    }
                    meta.shouldNotBeNull().apply {
                        values["requestedFromName"] shouldBe "Kari Normann"
                        values["requestedForMeteringPointId"] shouldBe "1234567890123"
                        values["requestedForMeteringPointAddress"] shouldBe "Example Street 1, 1234 Oslo"
                        values["balanceSupplierName"] shouldBe "Example Energy AS"
                        values["balanceSupplierContractName"] shouldBe "ExampleSupplierContract"
                    }
                }
                responseJson.links.shouldNotBeNull().apply {
                    self shouldBe "$REQUESTS_PATH/4f71d596-99e4-415e-946d-7252c1a40c50"
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }

            test("Should return the request for authenticated end user when using end-user token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestSingleResponse = response.body()
                responseJson.data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "AuthorizationRequest"
                    attributes.shouldNotBeNull().apply {
                        requestType shouldBe "ChangeOfSupplierConfirmation"
                        status shouldBe "Pending"

                        val validTo = validTo.shouldNotBeNull()
                        val createdAt = createdAt.shouldNotBeNull()
                        val updatedAt = updatedAt.shouldNotBeNull()

                        shouldNotThrowAny {
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                    relationships.shouldNotBeNull().apply {
                        requestedBy.apply {
                            data.apply {
                                id shouldBe "987654321"
                                type shouldBe "Organization"
                            }
                        }
                        requestedFrom.apply {
                            data.apply {
                                id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                type shouldBe "Person"
                            }
                        }
                        requestedTo.apply {
                            data.apply {
                                id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                type shouldBe "Person"
                            }
                        }
                    }
                    links.shouldNotBeNull().apply {
                        self.shouldNotBeNull()
                    }
                    meta.shouldNotBeNull().apply {
                        values["requestedFromName"] shouldBe "Ola Normann"
                        values["requestedForMeteringPointId"] shouldBe "1234567890123"
                        values["requestedForMeteringPointAddress"] shouldBe "Example Street 1, 1234 Oslo"
                        values["balanceSupplierName"] shouldBe "Example Energy AS"
                        values["balanceSupplierContractName"] shouldBe "ExampleSupplierContract"
                    }
                    links.shouldNotBeNull().apply {
                        self.shouldNotBeNull()
                    }
                }
                responseJson.links.shouldNotBeNull().apply {
                    self shouldBe "$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53"
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }

            test("Should return 404 on a nonexistent ID") {
                val response = client.get("$REQUESTS_PATH/167b1be9-f563-4b31-af1a-50439d567ee5") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "404"
                        code shouldBe "not_found"
                        title shouldBe "Not found"
                        detail shouldBe "The requested resource could not be found"
                    }
                }
            }

            test("Should return 403 when the request does not belong to the requester using maskinporten token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.Forbidden
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "403"
                        code shouldBe "not_authorized"
                        title shouldBe "Party not authorized"
                        detail shouldBe "The party is not allowed to access this resource"
                    }
                }
            }

            test("Should return 403 when the request does not belong to the requester using end user token") {
                val response = client.get("$REQUESTS_PATH/3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }
                response.status shouldBe HttpStatusCode.Forbidden
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "403"
                        code shouldBe "not_authorized"
                        title shouldBe "Party not authorized"
                        detail shouldBe "The party is not allowed to access this resource"
                    }
                }
            }
        }
    }

    context("POST /authorization-requests") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("Should return 201 Created") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        contentType(ContentType.Application.Json)
                        setBody(
                            JsonApiCreateRequest(
                                data =
                                JsonApiRequestResourceObjectWithMeta(
                                    type = "AuthorizationRequest",
                                    attributes =
                                    CreateRequestAttributes(
                                        requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                                    ),
                                    meta =
                                    CreateRequestMeta(
                                        requestedBy = PartyIdentifier(
                                            PartyIdentifierType.GlobalLocationNumber,
                                            "0107000000021"
                                        ),
                                        requestedFrom = PartyIdentifier(
                                            PartyIdentifierType.NationalIdentityNumber,
                                            "12345678901"
                                        ),
                                        requestedFromName = "Hillary Orr",
                                        requestedTo = PartyIdentifier(
                                            PartyIdentifierType.NationalIdentityNumber,
                                            "12345678902"
                                        ),
                                        requestedForMeteringPointId = "123456789012345678",
                                        requestedForMeteringPointAddress = "quaerendum",
                                        balanceSupplierName = "Balance Supplier",
                                        balanceSupplierContractName = "Selena Chandler",
                                    ),
                                ),
                            ),
                        )
                    }

                response.status shouldBe HttpStatusCode.Created

                val responseJson: CreateRequestResponse = response.body()
                responseJson.data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "AuthorizationRequest"
                    attributes.shouldNotBeNull().apply {
                        requestType shouldBe "ChangeOfSupplierConfirmation"
                        status shouldBe AuthorizationRequest.Status.Pending.name
                        val validTo = validTo.shouldNotBeNull()
                        shouldNotThrowAny {
                            JavaLocalDate.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                        val createdAt = createdAt.shouldNotBeNull()
                        val updatedAt = updatedAt.shouldNotBeNull()

                        shouldNotThrowAny {
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
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
                        values["balanceSupplierName"] shouldBe "Balance Supplier"
                        values["balanceSupplierContractName"] shouldBe "Selena Chandler"
                    }
                    links.shouldNotBeNull().apply {
                        self.shouldNotBeNull()
                    }
                }
                responseJson.links.shouldNotBeNull().apply {
                    self.shouldNotBeNull()
                }
                responseJson.meta.shouldNotBeNull().apply {
                    "createdAt".shouldNotBeNull()
                }
            }

            test("Should return 400 Bad Request on validation error with error payload") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        contentType(ContentType.Application.Json)
                        setBody(
                            JsonApiCreateRequest(
                                data =
                                JsonApiRequestResourceObjectWithMeta(
                                    type = "AuthorizationRequest",
                                    attributes =
                                    CreateRequestAttributes(
                                        requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                                    ),
                                    meta =
                                    CreateRequestMeta(
                                        requestedBy = PartyIdentifier(
                                            PartyIdentifierType.GlobalLocationNumber,
                                            "0107000000021"
                                        ),
                                        requestedFrom = PartyIdentifier(
                                            PartyIdentifierType.NationalIdentityNumber,
                                            "12345678901"
                                        ),
                                        requestedFromName = "",
                                        requestedTo = PartyIdentifier(
                                            PartyIdentifierType.NationalIdentityNumber,
                                            "12345678902"
                                        ),
                                        requestedForMeteringPointId = "123456789012345678",
                                        requestedForMeteringPointAddress = "quaerendum",
                                        balanceSupplierName = "Balance Supplier",
                                        balanceSupplierContractName = "Selena Chandler",
                                    ),
                                ),
                            ),
                        )
                    }

                response.status shouldBe HttpStatusCode.BadRequest

                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "400"
                        code shouldBe "missing_requested_from_name"
                        title shouldBe "Validation error"
                        detail shouldBe "Requested from name is missing"
                    }
                }
            }

            test("Should return 403 Forbidden when requestee has valid gridowner token") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer gridowner")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000038")
                        contentType(ContentType.Application.Json)
                        setBody(
                            JsonApiCreateRequest(
                                data =
                                JsonApiRequestResourceObjectWithMeta(
                                    type = "AuthorizationRequest",
                                    attributes =
                                    CreateRequestAttributes(
                                        requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                                    ),
                                    meta =
                                    CreateRequestMeta(
                                        requestedBy = PartyIdentifier(
                                            PartyIdentifierType.GlobalLocationNumber,
                                            "0107000000038"
                                        ),
                                        requestedFrom = PartyIdentifier(
                                            PartyIdentifierType.NationalIdentityNumber,
                                            "12345678901"
                                        ),
                                        requestedFromName = "Test Name",
                                        requestedTo = PartyIdentifier(
                                            PartyIdentifierType.NationalIdentityNumber,
                                            "12345678902"
                                        ),
                                        requestedForMeteringPointId = "123456789012345678",
                                        requestedForMeteringPointAddress = "quaerendum",
                                        balanceSupplierName = "Balance Supplier",
                                        balanceSupplierContractName = "Selena Chandler",
                                    ),
                                ),
                            ),
                        )
                    }
                response.status shouldBe HttpStatusCode.Forbidden

                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "403"
                        code shouldBe "unsupported_party_type"
                        title shouldBe "Unsupported party type"
                        detail shouldBe "The party type you are authorized as is not supported for this endpoint."
                    }
                }
            }
        }
    }

    context("PATCH /authorization-requests/{ID}") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("should not be accepted when validto has expired") {
                val patchResult =
                    client.patch("${REQUESTS_PATH}/130b6bca-1e3a-4653-8a9b-ccc0dc4fe389") {
                        header(HttpHeaders.Authorization, "Bearer enduser")
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

                patchResult.status shouldBe HttpStatusCode.BadRequest
                val responseJson: JsonApiErrorCollection = patchResult.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "400"
                        code shouldBe "expired_status_transition"
                        title shouldBe "Request has expired"
                        detail shouldBe "Request validity period has passed"
                    }
                }
            }

            test("Should accept authorization request and persist grant relationship") {
                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Kasper Lind",
                        "requestedForMeteringPointId" to "1234567890555",
                        "requestedForMeteringPointAddress" to "Example Street 2, 0654 Oslo",
                        "balanceSupplierName" to "Power AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val patchResult =
                    client.patch("${REQUESTS_PATH}/$requestId") {
                        header(HttpHeaders.Authorization, "Bearer enduser")
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

                patchResult.status shouldBe HttpStatusCode.OK
                val patchResponse: UpdateRequestResponse = patchResult.body()

                patchResponse.data.apply {
                    type shouldBe "AuthorizationRequest"
                    id.shouldNotBeNull()
                    attributes.shouldNotBeNull().apply {
                        status shouldBe "Accepted"
                        val validTo = validTo.shouldNotBeNull()
                        val createdAt = createdAt.shouldNotBeNull()
                        val updatedAt = updatedAt.shouldNotBeNull()

                        shouldNotThrowAny {
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                    relationships.shouldNotBeNull().apply {
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "987654321"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            requestedTo.apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            approvedBy.shouldNotBeNull().apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            grant.shouldNotBeNull().apply {
                                data.apply {
                                    id.shouldNotBeNull()
                                    type shouldBe "AuthorizationGrant"
                                }
                                links.shouldNotBeNull()
                            }
                            meta.shouldNotBeNull().apply {
                                values["requestedFromName"] shouldBe "Kasper Lind"
                                values["requestedForMeteringPointId"] shouldBe "1234567890555"
                                values["requestedForMeteringPointAddress"] shouldBe "Example Street 2, 0654 Oslo"
                                values["balanceSupplierName"] shouldBe "Power AS"
                                values["balanceSupplierContractName"] shouldBe "ExampleSupplierContract"
                            }
                        }
                    }
                }

                // verify that a subsequent GET of this resource reflects the updated state persisted in the database
                val getResult = client.get("${REQUESTS_PATH}/${patchResponse.data.id}") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }
                val getResponse: GetRequestSingleResponse = getResult.body()

                getResponse.data.apply {
                    type shouldBe "AuthorizationRequest"
                    id.shouldNotBeNull()
                    attributes.shouldNotBeNull().apply {
                        status shouldBe "Accepted"
                        val validTo = validTo.shouldNotBeNull()
                        val createdAt = createdAt.shouldNotBeNull()
                        val updatedAt = updatedAt.shouldNotBeNull()

                        shouldNotThrowAny {
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                    relationships.shouldNotBeNull().apply {
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "987654321"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            requestedTo.apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            approvedBy.shouldNotBeNull().apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            grant.shouldNotBeNull().apply {
                                data.apply {
                                    id.shouldNotBeNull()
                                    type shouldBe "AuthorizationGrant"
                                }
                                links.shouldNotBeNull()
                            }
                            meta.shouldNotBeNull().apply {
                                values["requestedFromName"] shouldBe "Kasper Lind"
                                values["requestedForMeteringPointId"] shouldBe "1234567890555"
                                values["requestedForMeteringPointAddress"] shouldBe "Example Street 2, 0654 Oslo"
                                values["balanceSupplierName"] shouldBe "Power AS"
                                values["balanceSupplierContractName"] shouldBe "ExampleSupplierContract"
                            }
                        }
                    }
                }
            }

            test("Should reject authorization-request") {
                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Ola Normann",
                        "requestedForMeteringPointId" to "1234567890123",
                        "requestedForMeteringPointAddress" to "Example Street 1, 1234 Oslo",
                        "balanceSupplierName" to "Example Energy AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val response =
                    client.patch("${REQUESTS_PATH}/$requestId") {
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        contentType(ContentType.Application.Json)
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    type = "AuthorizationRequest",
                                    attributes = UpdateRequestAttributes(
                                        status = AuthorizationRequest.Status.Rejected
                                    )
                                )
                            ),
                        )
                    }
                response.status shouldBe HttpStatusCode.OK

                val patchRequestResponse: UpdateRequestResponse = response.body()

                patchRequestResponse.data.apply {
                    type shouldBe "AuthorizationRequest"
                    id.shouldNotBeNull()
                    attributes.shouldNotBeNull().apply {
                        status shouldBe "Rejected"
                        val validTo = validTo.shouldNotBeNull()
                        val createdAt = createdAt.shouldNotBeNull()
                        val updatedAt = updatedAt.shouldNotBeNull()

                        shouldNotThrowAny {
                            OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            OffsetDateTime.parse(validTo, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                    relationships.shouldNotBeNull().apply {
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "987654321"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            requestedTo.apply {
                                data.apply {
                                    id shouldBe "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
                                    type shouldBe "Person"
                                }
                            }
                            meta.shouldNotBeNull().apply {
                                values["requestedFromName"] shouldBe "Ola Normann"
                                values["requestedForMeteringPointId"] shouldBe "1234567890123"
                                values["requestedForMeteringPointAddress"] shouldBe "Example Street 1, 1234 Oslo"
                                values["balanceSupplierName"] shouldBe "Example Energy AS"
                                values["balanceSupplierContractName"] shouldBe "ExampleSupplierContract"
                            }
                        }
                    }
                }
            }

            test("Should return 400 Bad Request on illegal transaction") {
                val requestId = insertAuthorizationRequest()
                val response =
                    client.patch("${REQUESTS_PATH}/$requestId") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    type = "AuthorizationRequest",
                                    attributes = UpdateRequestAttributes(
                                        status = AuthorizationRequest.Status.Expired
                                    )
                                )
                            ),
                        )
                    }

                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "400"
                        code shouldBe "invalid_status_transition"
                        title shouldBe "Invalid status transition"
                        detail shouldBe "Only 'Accepted' and 'Rejected' statuses are allowed."
                    }
                }
            }

            test("Should return 403 Unauthorized when requestee has valid maskinport token") {
                val response =
                    client.patch("${REQUESTS_PATH}/4f71d596-99e4-415e-946d-7352c1a40c53") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    type = "AuthorizationRequest",
                                    attributes = UpdateRequestAttributes(
                                        status = AuthorizationRequest.Status.Expired
                                    )
                                )
                            ),
                        )
                    }

                response.status shouldBe HttpStatusCode.Forbidden
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "403"
                        title shouldBe "Unsupported party type"
                        detail shouldBe "The party type you are authorized as is not supported for this endpoint."
                    }
                }
            }

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
            }
        }
    }
})

private fun ApplicationTestBuilder.setUpAuthorizationRequestTestApplication() {
    client = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    application {
        meteringPointsServiceModule()
        applicationModule()
        testRequestBusinessModule()
        commonModule()
        module()
    }

    environment {
        config =
            MapApplicationConfig(
                "ktor.database.username" to "app",
                "ktor.database.password" to "app",
                "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
                "ktor.database.driverClass" to "org.postgresql.Driver",
                "featureToggle.enableEndpoints" to "true",
                "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
                "pdp.baseUrl" to "http://localhost:8085",
                "structureData.meteringPointsService.serviceUrl" to "http://localhost:8086",
                "structureData.meteringPointsService.authentication.basic.username" to "user",
                "structureData.meteringPointsService.authentication.basic.password" to "password"
            )
    }
}

private fun insertAuthorizationRequest(
    status: AuthorizationRequest.Status = AuthorizationRequest.Status.Pending,
    validToDate: JavaLocalDate = JavaLocalDate.now().plusDays(10),
    properties: Map<String, String> = emptyMap()
): UUID {
    val requestId = UUID.randomUUID()
    val requestedById = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val requestedFromId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val requestedToId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    transaction {
        AuthorizationRequestTable.insert {
            it[id] = requestId
            it[requestType] = AuthorizationRequest.Type.ChangeOfSupplierConfirmation
            it[requestStatus] = status
            it[requestedBy] = requestedById
            it[requestedFrom] = requestedFromId
            it[requestedTo] = requestedToId
            it[approvedBy] = null
            it[validTo] = validToDate
        }

        if (properties.isNotEmpty()) {
            AuthorizationRequestPropertyTable.batchInsert(properties.entries) { (key, value) ->
                this[AuthorizationRequestPropertyTable.requestId] = requestId
                this[AuthorizationRequestPropertyTable.key] = key
                this[AuthorizationRequestPropertyTable.value] = value
            }
        }
    }

    return requestId
}

private class TestRequestBusinessHandler : RequestBusinessHandler {
    override fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel) = when (createRequestModel.requestType) {
        AuthorizationRequest.Type.ChangeOfSupplierConfirmation -> {
            val meta = createRequestModel.meta
            if (meta.requestedFromName.isBlank()) {
                TestRequestValidationError.MissingRequestedFromName.left()
            } else {
                RequestCommand(
                    type = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                    requestedFrom = meta.requestedFrom,
                    requestedBy = meta.requestedBy,
                    requestedTo = meta.requestedTo,
                    validTo = defaultRequestValidTo(),
                    meta = TestRequestMeta(
                        requestedFromName = meta.requestedFromName,
                        requestedForMeteringPointId = meta.requestedForMeteringPointId,
                        requestedForMeteringPointAddress = meta.requestedForMeteringPointAddress,
                        balanceSupplierName = meta.balanceSupplierName,
                        balanceSupplierContractName = meta.balanceSupplierContractName,
                    ),
                ).right()
            }
        }

        else -> TestRequestValidationError.UnsupportedRequestType.left()
    }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validFrom = no.elhub.auth.features.requests.create.model.today(),
            validTo = defaultRequestValidTo(),
        )
}

private data class TestRequestMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
) : RequestMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        mapOf(
            "requestedFromName" to requestedFromName,
            "requestedForMeteringPointId" to requestedForMeteringPointId,
            "requestedForMeteringPointAddress" to requestedForMeteringPointAddress,
            "balanceSupplierName" to balanceSupplierName,
            "balanceSupplierContractName" to balanceSupplierContractName,
        )
}

private sealed class TestRequestValidationError : RequestTypeValidationError {
    data object MissingRequestedFromName : TestRequestValidationError() {
        override val code: String = "missing_requested_from_name"
        override val message: String = "Requested from name is missing"
    }

    data object UnsupportedRequestType : TestRequestValidationError() {
        override val code: String = "unsupported_request_type"
        override val message: String = "Unsupported request type"
    }
}

private fun Application.testRequestBusinessModule() {
    koinModule {
        single<RequestBusinessHandler> { TestRequestBusinessHandler() }
    }
}
