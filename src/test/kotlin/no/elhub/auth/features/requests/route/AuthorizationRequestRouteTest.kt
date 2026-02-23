package no.elhub.auth.features.requests.route

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.elhub.auth.features.businessprocesses.BusinessProcessError
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
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
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
import no.elhub.auth.features.requests.create.model.today
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
            functionName = "BalanceSupplier",
            actingGln = "0107000000021"
        )
        pdpContainer.registerEnduserMapping(
            token = "enduser",
            partyId = "17abdc56-8f6f-440a-9f00-b9bfbb22065e"
        )
        pdpContainer.registerMaskinportenMapping(
            token = "gridowner",
            functionName = "GridOwner",
            actingGln = "0107000000038"
        )
        pdpContainer.registerInvalidTokenMapping()
    }

    context("Happy path") {
        testApplication {
            setUpAuthorizationRequestTestApplication()

            test("GET /authorization-requests/ should return requests sorted by createdAt datetime timestamp") {
                val response = client.get(REQUESTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }

                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestCollectionResponse = response.body()

                val createdAtList = responseJson.data
                    .map { OffsetDateTime.parse(it.attributes.createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }

                createdAtList shouldBe createdAtList.sortedDescending()
            }

            test("GET /authorization-requests/ should return only requests for authenticated organization when using Maskinporten token") {
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

            test("GET /authorization-requests/ should return only requests for authenticated end user when using end-user token") {
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

            test("GET /authorization-requests/ should return empty list when authorized organization has no requests") {
                pdpContainer.registerMaskinportenMapping(
                    token = "no-requests",
                    actingGln = "0107000000022",
                    functionName = "BalanceSupplier",
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

            test("GET /authorization-requests/{id} should return the request for authenticated organization when using Maskinporten token") {
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
                        requestType shouldBe "ChangeOfEnergySupplierForPerson"
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
                        authorizationGrant.shouldNotBeNull().apply {
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

            test("GET /authorization-requests/{id} should return the request for authenticated end user when using end-user token") {
                val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7352c1a40c53") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                }
                response.status shouldBe HttpStatusCode.OK
                val responseJson: GetRequestSingleResponse = response.body()
                responseJson.data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "AuthorizationRequest"
                    attributes.shouldNotBeNull().apply {
                        requestType shouldBe "ChangeOfEnergySupplierForPerson"
                        status shouldBe "Expired"

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
                        values["redirectURI"] shouldBe "https://example.com/redirect"
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

            test("POST /authorization-requests/ should return 201 Created") {
                val result = client.post(REQUESTS_PATH) {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                    contentType(ContentType.Application.Json)
                    setBody(examplePostBody)
                }
                result.status shouldBe HttpStatusCode.Created

                val responseJson: CreateRequestResponse = result.body()
                responseJson.data.apply {
                    id.shouldNotBeNull()
                    type shouldBe "AuthorizationRequest"
                    attributes.shouldNotBeNull().apply {
                        requestType shouldBe "ChangeOfEnergySupplierForPerson"
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
                        values["redirectURI"] shouldBe "https://example.com/redirect"
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


            test("PATCH /authorization-requests/ should accept authorization request and persist grant relationship") {
                val requestId = insertAuthorizationRequest(
                    properties = mapOf(
                        "requestedFromName" to "Kasper Lind",
                        "requestedForMeteringPointId" to "1234567890555",
                        "requestedForMeteringPointAddress" to "Example Street 2, 0654 Oslo",
                        "balanceSupplierName" to "Power AS",
                        "balanceSupplierContractName" to "ExampleSupplierContract"
                    )
                )
                val patchResult = client.patch("${REQUESTS_PATH}/$requestId") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                    contentType(ContentType.Application.Json)
                    setBody(
                        JsonApiUpdateRequest(
                            data = JsonApiRequestResourceObject(
                                id = requestId.toString(),
                                type = "AuthorizationRequest",
                                attributes = UpdateRequestAttributes(
                                    status = AuthorizationRequest.Status.Accepted
                                )
                            )
                        )
                    )
                }
                println("RESULT: ${patchResult.bodyAsText()}")

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
                            authorizationGrant.shouldNotBeNull().apply {
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
        }
    }

    context("Edge cases and incorrect inputs") {
        testApplication {
            setUpAuthorizationRequestTestApplication()
            test("GET /authorization-requests/{id} should return 404 on a nonexistent ID") {
                val response = client.get("$REQUESTS_PATH/167b1be9-f563-4b31-af1a-50439d567ee5") {
                    header(HttpHeaders.Authorization, "Bearer maskinporten")
                    header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                }
                response.status shouldBe HttpStatusCode.NotFound

            }
            test("Should return 409 Conflict on invalid data.type") {
                val requestIdParam = "130b6bca-1e3a-4653-8a9b-ccc0dc4fe389"
                val response = client.patch("${REQUESTS_PATH}/$requestIdParam") {
                    header(HttpHeaders.Authorization, "Bearer enduser")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                {
                  "data": {
                    "id" : "$requestIdParam",
                    "type": "test",
                    "attributes": {
                        "status": "Accepted"
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
                        detail shouldBe "Expected 'data.type' to be 'AuthorizationRequest', but received 'test'"
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
            test("POST /authorization-requests/ should return 409 Conflict on invalid data.type") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                {
                  "data": {
                  "type": "test"
                    "attributes": {
                      "requestType": "ChangeOfEnergySupplierForPerson"
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
                        detail shouldBe "Expected 'data.type' to be 'AuthorizationRequest', but received 'test'"
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
            test("PATCH /authorization-requests/{id} should return 409 Conflict on mismatch id") {
                val requestId = insertAuthorizationRequest()
                val response =
                    client.patch("${REQUESTS_PATH}/$requestId") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    id = "1234",
                                    type = "AuthorizationRequest",
                                    attributes = UpdateRequestAttributes(
                                        status = AuthorizationRequest.Status.Expired
                                    )
                                )
                            ),
                        )
                    }
                response.status shouldBe HttpStatusCode.Conflict
                val responseJson: JsonApiErrorCollection = response.body()
                responseJson.errors.apply {
                    size shouldBe 1
                    this[0].apply {
                        status shouldBe "409"
                        title shouldBe "Resource id mismatch"
                        detail shouldBe "Expected 'data.id' to be the same as in URL path {id}"
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }

            test("PATCH /authorization-requests/{id} should return 400 Bad Request on illegal transaction") {
                val requestId = insertAuthorizationRequest()
                val response =
                    client.patch("${REQUESTS_PATH}/$requestId") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    id = requestId.toString(),
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
                        title shouldBe "Invalid status transition"
                        detail shouldBe "Only 'Accepted' and 'Rejected' statuses are allowed."
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
            test("PATCH /authorization-requests/{id} should not be accepted when validTo has expired") {
                val id = "130b6bca-1e3a-4653-8a9b-ccc0dc4fe389"
                val patchResult =
                    client.patch("${REQUESTS_PATH}/$id") {
                        header(HttpHeaders.Authorization, "Bearer enduser")
                        contentType(ContentType.Application.Json)
                        setBody(
                            JsonApiUpdateRequest(
                                data = JsonApiRequestResourceObject(
                                    id = id,
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
                        title shouldBe "Request has expired"
                        detail shouldBe "Request validity period has passed"
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
            test("POST /authorization-requests/ should return 400 Bad Request on missing field in request body") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                {
                  "data": {
                    "type": "AuthorizationRequest"
                    "attributes": {
                      "requestType": "ChangeOfEnergySupplierForPerson"
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

            test("POST /authorization-requests/ should return 400 Bad Request on invalid field value in request body") {
                val response =
                    client.post(REQUESTS_PATH) {
                        header(HttpHeaders.Authorization, "Bearer maskinporten")
                        header(PDPAuthorizationProvider.Companion.Headers.SENDER_GLN, "0107000000021")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                {
                  "data": {
                    "type": "AuthorizationRequest",
                    "attributes": {
                      "requestType": "ChangeOfEnergySupplierForPerson"
                    },
                    "meta": {
                      "requestedBy": { "idType": "TEST", "id": "0107000000021", "idValue": "0107000000020"" },
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

            test("POST /authorization-requests/ should return 400 Bad Request on validation error with error payload") {
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
                                                requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                                            ),
                                        meta =
                                            CreateRequestMeta(
                                                requestedBy = PartyIdentifier(
                                                    PartyIdentifierType.GlobalLocationNumber,
                                                    "0107000000021"
                                                ),
                                                requestedFrom = PartyIdentifier(
                                                    PartyIdentifierType.NationalIdentityNumber,
                                                    REQUESTED_FROM_NIN
                                                ),
                                                requestedFromName = "",
                                                requestedTo = PartyIdentifier(
                                                    PartyIdentifierType.NationalIdentityNumber,
                                                    REQUESTED_TO_NIN
                                                ),
                                                requestedForMeteringPointId = "123456789012345678",
                                                requestedForMeteringPointAddress = "quaerendum",
                                                balanceSupplierName = "Balance Supplier",
                                                balanceSupplierContractName = "Selena Chandler",
                                                redirectURI = "https://example.com",
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

                        title shouldBe "Validation error"
                        detail shouldBe "Requested from name is missing"
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
            test("POST /authorization-requests/ should return 400 Bad Request when requestee has invalid nin in body") {
                val response = client.post(REQUESTS_PATH) {
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
                                            requestType = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                                        ),
                                    meta =
                                        CreateRequestMeta(
                                            requestedBy = PartyIdentifier(
                                                PartyIdentifierType.GlobalLocationNumber,
                                                "0107000000021"
                                            ),
                                            requestedFrom = PartyIdentifier(
                                                PartyIdentifierType.NationalIdentityNumber,
                                                "123"
                                            ),
                                            requestedFromName = "Hillary Orr",
                                            requestedTo = PartyIdentifier(
                                                PartyIdentifierType.NationalIdentityNumber,
                                                REQUESTED_TO_NIN
                                            ),
                                            requestedForMeteringPointId = "123456789012345678",
                                            requestedForMeteringPointAddress = "quaerendum",
                                            balanceSupplierName = "Balance Supplier",
                                            balanceSupplierContractName = "Selena Chandler",
                                            redirectURI = "https://example.com",
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
                        title shouldBe "Invalid national identity number"
                        detail shouldBe "Provided national identity number is invalid"
                    }
                }
                responseJson.meta.apply {
                    "createdAt".shouldNotBeNull()
                }
            }
        }
    }
})
