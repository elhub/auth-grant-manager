package no.elhub.auth.features.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.requests.common.AuthorizationRequestListResponse
import no.elhub.auth.features.requests.common.AuthorizationRequestResponse
import no.elhub.auth.features.requests.create.CreateRequestAttributes
import no.elhub.auth.features.requests.create.CreateRequestRelationships
import no.elhub.auth.features.requests.create.CreateRequestRequest
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithRelationships
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.auth.module as applicationModule

class AuthorizationRequestRouteTest : FunSpec({
    extensions(
        PostgresTestContainerExtension,
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql")
    )

    context("GET /authorization-requests") {
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
                )
            }
            test("Should return 200 OK") {
                val response = client.get(REQUESTS_PATH)
                response.status shouldBe HttpStatusCode.OK
                val responseJson: AuthorizationRequestListResponse = response.body()
                responseJson.data.apply {
                    size shouldBe 2
                    this[0].apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe "Pending"
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            validTo.shouldNotBeNull()
                        }
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "84797600005"
                                    type shouldBe "Organization"
                                }
                            }

                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "80102512345"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        meta.shouldNotBeNull()
                        meta!!
                            .mapValues { it.value.jsonPrimitive.content }
                            .apply {
                                shouldContainKey("createdAt")
                                shouldContain("requestedFromName", "Ola Normann")
                                shouldContain("requestedForMeteringPointId" to "1234567890123")
                                shouldContain("requestedForMeteringPointAddress" to "Example Street 1, 1234 Oslo")
                                shouldContain("balanceSupplierContractName" to "ExampleSupplierContract")
                            }
                    }
                    this[1].apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe "Accepted"
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            validTo.shouldNotBeNull()
                        }
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "84797600005"
                                    type shouldBe "Organization"
                                }
                            }

                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "80102512345"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        meta.shouldNotBeNull()
                        meta!!
                            .mapValues { it.value.jsonPrimitive.content }
                            .apply {
                                shouldContainKey("createdAt")
                                shouldContain("requestedFromName" to "Kari Normann")
                                shouldContain("requestedForMeteringPointId" to "1234567890123")
                                shouldContain("requestedForMeteringPointAddress" to "Example Street 1, 1234 Oslo")
                                shouldContain("balanceSupplierContractName" to "ExampleSupplierContract")
                            }
                    }
                }
            }
        }
        context("GET /authorization-requests/{id}") {
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
                    )
                }
                test("Should return 200 OK on a valid ID") {
                    val response = client.get("$REQUESTS_PATH/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6")
                    response.status shouldBe HttpStatusCode.OK
                    val responseJson: AuthorizationRequestResponse = response.body()

                    responseJson.data.apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe "Pending"
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            validTo.shouldNotBeNull()
                        }
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "84797600005"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "80102512345"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        meta.shouldNotBeNull()
                        meta!!
                            .mapValues { it.value.jsonPrimitive.content }
                            .apply {
                                shouldContainKey("createdAt")
                                shouldContain("requestedFromName", "Ola Normann")
                                shouldContain("requestedForMeteringPointId" to "1234567890123")
                                shouldContain("requestedForMeteringPointAddress" to "Example Street 1, 1234 Oslo")
                                shouldContain("balanceSupplierContractName" to "ExampleSupplierContract")
                            }
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
        context("POST /authorization-requests ") {
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
                    )
                }
                test("Should return 201 Created") {
                    val response = client.post(REQUESTS_PATH) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            CreateRequestRequest(
                                data = JsonApiRequestResourceObjectWithRelationships<CreateRequestAttributes, CreateRequestRelationships>(
                                    type = "AuthorizationRequest",
                                    attributes = CreateRequestAttributes(
                                        requestType = "ChangeOfSupplierConfirmation",
                                    ),
                                    relationships = CreateRequestRelationships(
                                        requestedBy = JsonApiRelationshipToOne(
                                            data = JsonApiRelationshipData(
                                                id = "12345678901",
                                                type = "Organization",
                                            )
                                        ),
                                        requestedFrom = JsonApiRelationshipToOne(
                                            data = JsonApiRelationshipData(
                                                id = "98765432109",
                                                type = "Person",
                                            )
                                        )
                                    ),
                                ),
                            )
                        )
                    }

                    response.status shouldBe HttpStatusCode.Created
                    val responseJson: AuthorizationRequestResponse = response.body()
                    responseJson.data.apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull()
                        attributes!!.apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe "Pending"
                            createdAt.shouldNotBeNull()
                            updatedAt.shouldNotBeNull()
                            validTo.shouldNotBeNull()
                        }
                        relationships.apply {
                            requestedBy.apply {
                                data.apply {
                                    id shouldBe "12345678901"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "98765432109"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        meta.shouldNotBeNull()
                        meta!!
                            .mapValues { it.value.jsonPrimitive.content }
                            .apply {
                                shouldContainKey("createdAt")
                            }
                    }
                }
            }
        }
    }
})
