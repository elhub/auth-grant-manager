package no.elhub.auth.features.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.config.AUTHORIZATION_REQUEST
import no.elhub.auth.extensions.PostgresTestContainerExtension
import no.elhub.auth.extensions.RunPostgresScriptExtension
import no.elhub.auth.utils.defaultTestApplication
import no.elhub.auth.validate

class AuthorizationRequestRouteTest :
    FunSpec({
        extensions(
            PostgresTestContainerExtension,
            RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql")
        )

        lateinit var testApp: TestApplication

        beforeSpec {
            testApp = defaultTestApplication()
        }

        afterSpec {
            testApp.stop()
        }

        context("GET /authorization-requests") {

            test("Should return 200 OK") {
                val response = testApp.client.get(AUTHORIZATION_REQUEST)
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data".shouldBeList(size = 2) {
                        item(0) {
                            "id".shouldNotBeNull()
                            "type" shouldBe "AuthorizationRequest"
                            "attributes" {
                                "requestType" shouldBe "ChangeOfSupplierConfirmation"
                                "status" shouldBe "Pending"
                                "createdAt".shouldNotBeNull()
                                "updatedAt".shouldNotBeNull()
                                "validTo".shouldNotBeNull()
                            }
                            "relationships" {
                                "requestedBy" {
                                    "data" {
                                        "id" shouldBe "84797600005"
                                        "type" shouldBe "Organization"
                                    }
                                }
                                "requestedFrom" {
                                    "data" {
                                        "id" shouldBe "80102512345"
                                        "type" shouldBe "Person"
                                    }
                                }
                            }
                            "meta" {
                                "createdAt".shouldNotBeNull()
                                "contract" shouldBe "SampleContract1"
                            }
                        }
                        item(1) {
                            "id".shouldNotBeNull()
                            "type" shouldBe "AuthorizationRequest"
                            "attributes" {
                                "requestType" shouldBe "ChangeOfSupplierConfirmation"
                                "status" shouldBe "Accepted"
                                "createdAt".shouldNotBeNull()
                                "updatedAt".shouldNotBeNull()
                                "validTo".shouldNotBeNull()
                            }
                            "relationships" {
                                "requestedBy" {
                                    "data" {
                                        "id" shouldBe "84797600005"
                                        "type" shouldBe "Organization"
                                    }
                                }
                                "requestedFrom" {
                                    "data" {
                                        "id" shouldBe "80102512345"
                                        "type" shouldBe "Person"
                                    }
                                }
                            }
                            "meta" {
                                "createdAt".shouldNotBeNull()
                                "contract" shouldBe "SampleContract2"
                            }
                        }
                    }
                }
            }
        }

        context("GET /authorization-requests/{id}") {

            test("Should return 200 OK on a valid ID") {
                val response = testApp.client.get("$AUTHORIZATION_REQUEST/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6")
                response.status shouldBe HttpStatusCode.OK

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data" {
                        "id".shouldNotBeNull()
                        "type" shouldBe "AuthorizationRequest"
                        "attributes" {
                            "requestType" shouldBe "ChangeOfSupplierConfirmation"
                            "status" shouldBe "Pending"
                            "createdAt".shouldNotBeNull()
                            "updatedAt".shouldNotBeNull()
                            "validTo".shouldNotBeNull()
                        }
                        "relationships" {
                            "requestedBy" {
                                "data" {
                                    "id" shouldBe "84797600005"
                                    "type" shouldBe "Organization"
                                }
                            }
                            "requestedFrom" {
                                "data" {
                                    "id" shouldBe "80102512345"
                                    "type" shouldBe "Person"
                                }
                            }
                        }
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                        "contract" shouldBe "SampleContract1"
                    }
                }
            }

            test("Should return 400 Bad Request on an invalid ID format") {
                val response = testApp.client.get("$AUTHORIZATION_REQUEST/invalid-id")
                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "400"
                            "title" shouldBe "Bad Request"
                            "detail" shouldBe "Missing or malformed id."
                        }
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-requests/invalid-id"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }

            test("Should return 404 Not Found on an invalid ID that is a UUID") {
                val response = testApp.client.get("$AUTHORIZATION_REQUEST/167b1be9-f563-4b31-af1a-50439d567ee5")
                response.status shouldBe HttpStatusCode.NotFound
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "404"
                            "title" shouldBe "Not Found"
                            "detail" shouldBe "Authorization request with id=167b1be9-f563-4b31-af1a-50439d567ee5 not found"
                        }
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-requests/167b1be9-f563-4b31-af1a-50439d567ee5"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }
        }

        context("POST /authorization-requests") {

            test("Should return 201 Created") {
                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                                "data": {
                                    "type": "AuthorizationRequest",
                                    "attributes": {
                                        "requestType": "ChangeOfSupplierConfirmation"
                                    },
                                    "relationships": {
                                        "requestedBy": {
                                            "data": {
                                                "id": "12345678901",
                                                "type": "Organization"
                                            }
                                        },
                                        "requestedFrom": {
                                            "data": {
                                                "id": "98765432109",
                                                "type": "Person"
                                            }
                                        }
                                    }

                                }
                            }
                            """.trimIndent()
                        )
                    }

                response.status shouldBe HttpStatusCode.Created

                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "data" {
                        "id".shouldNotBeNull()
                        "type" shouldBe "AuthorizationRequest"
                        "attributes" {
                            "requestType" shouldBe "ChangeOfSupplierConfirmation"
                            "status" shouldBe "Pending"
                            "createdAt".shouldNotBeNull()
                            "updatedAt".shouldNotBeNull()
                            "validTo".shouldNotBeNull()
                        }
                        "relationships" {
                            "requestedBy" {
                                "data" {
                                    "id" shouldBe "12345678901"
                                    "type" shouldBe "Organization"
                                }
                            }
                            "requestedFrom" {
                                "data" {
                                    "id" shouldBe "98765432109"
                                    "type" shouldBe "Person"
                                }
                            }
                        }
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }

            // This passes because the JSON serializer is lenient and allows for extra fields.
            // Switch on when stricter validation is added (e.g., schema validation).
            xtest("Should return 400 Bad Request if the requestBody is invalid (added extra attribute)") {
                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                                "data": {
                                    "type": "AuthorizationRequest",
                                    "attributes": {
                                        "requestType": "ChangeOfSupplierConfirmation",
                                        "foo": "bar"
                                    },
                                    "relationships": {
                                        "requestedBy": {
                                            "data": {
                                                "id": "0847976000005",
                                                "type": "Organization"
                                            }
                                        },
                                        "requestedFrom": {
                                            "data": {
                                                "id": "80102512345",
                                                "type": "Person"
                                            }
                                        }
                                    }
                                }
                            }
                            """.trimIndent()
                        )
                    }

                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "400"
                            "title" shouldBe "Bad Request"
                            "detail" shouldBe "Authorization request contains extra, unknown, or missing fields. "
                        }
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-requests"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }

            test("Should return 400 Bad Request if the requestBody is invalid (missing attribute)") {
                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                                "data": {
                                    "type": "AuthorizationRequest",
                                    "relationships": {
                                        "requestedBy": {
                                            "data": {
                                                "id": "0847976000005",
                                                "type": "Organization"
                                            }
                                        },
                                        "requestedFrom": {
                                            "data": {
                                                "id": "80102512345",
                                                "type": "Person"
                                            }
                                        }
                                    }
                                }
                            }
                            """.trimIndent()
                        )
                    }

                response.status shouldBe HttpStatusCode.BadRequest
                val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseJson.validate {
                    "errors".shouldBeList(size = 1) {
                        item(0) {
                            "status" shouldBe "400"
                            "title" shouldBe "Bad Request"
                            "detail" shouldBe "Authorization request contains extra, unknown, or missing fields. "
                        }
                    }
                    "links" {
                        "self" shouldBe "http://localhost/authorization-requests"
                    }
                    "meta" {
                        "createdAt".shouldNotBeNull()
                    }
                }
            }
        }
    })
