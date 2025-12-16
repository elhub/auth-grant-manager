package no.elhub.auth.features.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.AuthPersonsTestContainerExtension
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.common.commonModule
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.requests.create.dto.CreateRequestAttributes
import no.elhub.auth.features.requests.create.dto.CreateRequestMeta
import no.elhub.auth.features.requests.create.dto.CreateRequestResponse
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.features.requests.get.dto.GetRequestSingleResponse
import no.elhub.auth.features.requests.query.dto.GetRequestCollectionResponse
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.UpdateRequestAttributes
import no.elhub.auth.features.requests.update.dto.UpdateRequestResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObject
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject
import no.elhub.auth.module as applicationModule

class AuthorizationRequestRouteTest :
    FunSpec({
        extensions(
            AuthPersonsTestContainerExtension,
            PostgresTestContainerExtension(),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-scopes.sql"),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"),
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-grants.sql"),
        )

        context("GET /authorization-requests") {
            testApplication {
                client =
                    createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }

                application {
                    applicationModule()
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
                        )
                }

                test("GET all authorization request should return 200 OK") {
                    val response = client.get(REQUESTS_PATH)
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: GetRequestCollectionResponse = response.body()
                    responseJson.data.apply {
                        size shouldBe 5
                    }
                }
            }
        }

        context("GET /authorization-requests/{id}") {
            testApplication {
                client =
                    createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }

                application {
                    applicationModule()
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
                        )
                }

                test("Should return 200 OK on a valid ID before request is accepted") {
                    val response = client.get("$REQUESTS_PATH/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6")
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: GetRequestSingleResponse = response.body()
                    responseJson.data.apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull().apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe "Pending"
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            validTo.shouldNotBeNull()
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
                                    id shouldBe "4e55f1e2-e576-23ab-80d3-c70a6fe354c0"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        links.shouldNotBeNull()
                        links.apply {
                            self.shouldNotBeNull()
                        }
                    }
                    responseJson.links.apply {
                        self shouldBe REQUESTS_PATH
                    }
                    responseJson.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Should return 200 OK with approvedBy on a valid ID after request is accepted") {
                    val response = client.get("$REQUESTS_PATH/4f71d596-99e4-415e-946d-7252c1a40c5b")
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: GetRequestSingleResponse = response.body()
                    responseJson.data.apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull().apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe "Accepted"
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            validTo.shouldNotBeNull()
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
                                    id shouldBe "4e55f1e2-e576-23ab-80d3-c70a6fe354c0"
                                    type shouldBe "Person"
                                }
                            }
                            approvedBy.shouldNotBeNull().apply {
                                data.apply {
                                    id shouldBe "4e55f1e2-e576-23ab-80d3-c70a6fe354c0"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        links.shouldNotBeNull()
                        links.apply {
                            self.shouldNotBeNull()
                        }
                    }
                    responseJson.links.apply {
                        self shouldBe REQUESTS_PATH
                    }
                    responseJson.meta.apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Should return 400 on an invalid ID format") {
                    val response = client.get("$REQUESTS_PATH/invalid-id")
                    response.status shouldBe HttpStatusCode.BadRequest
                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "400"
                            code shouldBe "INVALID_INPUT"
                            title shouldBe "Invalid input"
                            detail shouldBe "The provided payload did not satisfy the expected format"
                        }
                    }
                }

                test("Should return 404 on a nonexistent ID") {
                    val response = client.get("$REQUESTS_PATH/167b1be9-f563-4b31-af1a-50439d567ee5")
                    response.status shouldBe HttpStatusCode.NotFound
                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "404"
                            code shouldBe "NOT_FOUND"
                            title shouldBe "Not Found"
                            detail shouldBe "The requested resource could not be found"
                        }
                    }
                }
            }
        }

        context("POST /authorization-requests") {
            testApplication {
                client = createClient { install(ContentNegotiation) { json() } }

                application {
                    applicationModule()
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
                        )
                }

                test("Should return 201 Created") {
                    val response =
                        client.post(REQUESTS_PATH) {
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
                                            requestedBy = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "987654321"),
                                            requestedFrom = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678901"),
                                            requestedFromName = "Hillary Orr",
                                            requestedTo = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678902"),
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
                            validTo.shouldNotBeNull()
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
                                        id.shouldNotBeNull()
                                        type shouldBe "Person"
                                    }
                                }
                                requestedFrom.apply {
                                    data.apply {
                                        id.shouldNotBeNull()
                                        type shouldBe "Person"
                                    }
                                }
                            }
                        }
                        meta.shouldNotBeNull().apply {
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            requestedFromName shouldBe "Hillary Orr"
                            requestedForMeteringPointId shouldBe "123456789012345678"
                            requestedForMeteringPointAddress shouldBe "quaerendum"
                            balanceSupplierName shouldBe "Balance Supplier"
                            balanceSupplierContractName shouldBe "Selena Chandler"
                        }
                        links.shouldNotBeNull().apply {
                            self.shouldNotBeNull()
                        }
                    }
                    responseJson.links.shouldNotBeNull().apply {
                        self shouldBe REQUESTS_PATH
                    }
                    responseJson.meta.shouldNotBeNull().apply {
                        "createdAt".shouldNotBeNull()
                    }
                }

                test("Should return 400 Bad Request on validation error with error payload") {
                    val response =
                        client.post(REQUESTS_PATH) {
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
                                            requestedBy = PartyIdentifier(PartyIdentifierType.OrganizationNumber, "987654321"),
                                            requestedFrom = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678901"),
                                            requestedFromName = "",
                                            requestedTo = PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "12345678902"),
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
                    val body = response.body<JsonApiErrorObject>()
                    body.status shouldBe "400"
                    body.code shouldBe "missing_requested_from_name"
                    body.title shouldBe "Validation Error"
                    body.detail shouldBe "Requested from name is missing"
                }
            }
        }

        context("PATCH /authorization-requests/{ID}") {
            testApplication {
                client =
                    createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }

                application {
                    applicationModule()
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
                        )
                }

                test("Should accept authorization-request") {
                    val response =
                        client.patch("${REQUESTS_PATH}/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6") {
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
                    response.status shouldBe HttpStatusCode.OK
                    val patchRequestResponse: UpdateRequestResponse = response.body()

                    patchRequestResponse.data.apply {
                        type shouldBe "AuthorizationRequest"
                        id.shouldNotBeNull()
                        attributes.shouldNotBeNull().apply {
                            status shouldBe "Accepted"
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
                                        id shouldBe "4e55f1e2-e576-23ab-80d3-c70a6fe354c0"
                                        type shouldBe "Person"
                                    }
                                }
                                approvedBy?.apply {
                                    data.apply {
                                        id shouldBe "4e55f1e2-e576-23ab-80d3-c70a6fe354c0"
                                        type shouldBe "Person"
                                    }
                                }
                                grant?.apply {
                                    data.apply {
                                        id.shouldNotBeNull()
                                        type shouldBe "AuthorizationGrant"
                                    }
                                    links.shouldNotBeNull()
                                }
                            }
                        }
                    }
                }

                test("Should reject authorization-request") {
                    val response =
                        client.patch("${REQUESTS_PATH}/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6") {
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
                                        id shouldBe "4e55f1e2-e576-23ab-80d3-c70a6fe354c0"
                                        type shouldBe "Person"
                                    }
                                }
                            }
                        }
                    }
                }

                test("Should return 400 Bad Request on illegal transaction") {
                    val response =
                        client.patch("${REQUESTS_PATH}/3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47") {
                            contentType(ContentType.Application.Json)
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
                    val body = response.body<JsonApiErrorObject>()
                    body.status shouldBe "400"
                    body.title shouldBe "Invalid Status Transition"
                    body.detail shouldBe "Only 'Accepted' and 'Rejected' statuses are allowed."
                }
            }
        }
    })
