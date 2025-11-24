package no.elhub.auth.features.requests

import io.kotest.core.spec.style.FunSpec
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
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.common.PartyIdentifierType
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.RunPostgresScriptExtension
import no.elhub.auth.features.requests.common.AuthorizationRequestListResponse
import no.elhub.auth.features.requests.common.AuthorizationRequestResponse
import no.elhub.auth.features.requests.create.CreateRequest
import no.elhub.auth.features.requests.create.CreateRequestAttributes
import no.elhub.auth.features.requests.create.CreateRequestMeta
import no.elhub.auth.features.requests.create.CreateRequestResponse
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithMeta
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.auth.module as applicationModule

class AuthorizationRequestRouteTest : FunSpec({
    extensions(
        AuthPersonsTestContainerExtension,
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-party.sql"),
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
                    "featureToggle.enableEndpoints" to "true",
                    "authPersons.baseUri" to AuthPersonsTestContainer.baseUri()
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
                                    id shouldBe "987654321"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "12345678901"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        links.shouldNotBeNull()
                        links!!.apply {
                            self.shouldNotBeNull()
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
                                    id shouldBe "987654321"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "12345678901"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        links.shouldNotBeNull()
                        links!!.apply {
                            self.shouldNotBeNull()
                        }
                    }
                }
            }
        }

        // TODO for some reason this is filtered out when context is "GET /authorization-requests/{id}"
        context("GET authorization request by id ") {
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
                        "featureToggle.enableEndpoints" to "true"
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
                                    id shouldBe "987654321"
                                    type shouldBe "Organization"
                                }
                            }
                            requestedFrom.apply {
                                data.apply {
                                    id shouldBe "12345678901"
                                    type shouldBe "Person"
                                }
                            }
                        }
                        links.shouldNotBeNull()
                        links!!.apply {
                            self.shouldNotBeNull()
                        }
                    }
                    responseJson.links.apply {
                        self shouldBe "/authorization-requests"
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
                        "featureToggle.enableEndpoints" to "true",
                        "authPersons.baseUri" to AuthPersonsTestContainer.baseUri()
                    )
                }
                test("Should return 201 Created") {
                    val response = client.post(REQUESTS_PATH) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            CreateRequest(
                                data = JsonApiRequestResourceObjectWithMeta(
                                    type = "AuthorizationRequest",
                                    attributes = CreateRequestAttributes(
                                        requestType = AuthorizationRequest.Type.ChangeOfSupplierConfirmation
                                    ),
                                    meta = CreateRequestMeta(
                                        requestedBy = PartyIdentifier(
                                            idType = PartyIdentifierType.OrganizationNumber,
                                            idValue = "987654321"
                                        ),
                                        requestedFrom = PartyIdentifier(
                                            idType = PartyIdentifierType.NationalIdentityNumber,
                                            idValue = "12345678901"
                                        ),
                                        requestedFromName = "Hillary Orr",
                                        requestedForMeteringPointId = "atomorum",
                                        requestedForMeteringPointAddress = "quaerendum",
                                        balanceSupplierContractName = "Selena Chandler"
                                    )
                                )
                            )
                        )
                    }

                    response.status shouldBe HttpStatusCode.Created
                    val responseJson: CreateRequestResponse = response.body()
                    responseJson.data.apply {
                        id.shouldNotBeNull()
                        type shouldBe "AuthorizationRequest"
                        attributes.shouldNotBeNull()
                        attributes.apply {
                            requestType shouldBe "ChangeOfSupplierConfirmation"
                            status shouldBe AuthorizationRequest.Status.Pending.name
                        }
                        links.shouldNotBeNull()
                        links.apply {
                            self.shouldNotBeNull()
                        }
                    }
                    responseJson.links.apply {
                        self shouldBe "/authorization-requests"
                    }
                }
            }
        }
    }
})
