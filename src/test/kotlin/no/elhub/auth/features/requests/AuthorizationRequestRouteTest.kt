package no.elhub.auth.features.requests

import io.kotest.assertions.json.shouldBeValidJson
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplication
import no.elhub.auth.config.AUTHORIZATION_REQUEST
import no.elhub.auth.utils.defaultTestApplication

class AuthorizationRequestRouteTest : DescribeSpec({

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    // GET /authorization-requests
    describe("GET $AUTHORIZATION_REQUEST") {

        it("should return 200 OK") {
            val response = testApp.client.get(AUTHORIZATION_REQUEST)
            response.status shouldBe HttpStatusCode.OK

            val responseString = response.bodyAsText()
            responseString.shouldBeValidJson()

            val expectedJson = """
                {
                    "data":[
                        {
                            "id": "d81e5bf2-8a0c-4348-a788-2a3fab4e77d6",
                            "type": "AuthorizationRequest",
                            "attributes": {
                                "status": "Pending",
                                "validTo": "2025-04-04T02:00"
                            },
                            "relationships": {
                                "requestedBy": {
                                    "data": {
                                        "id": "0847976000005",
                                        "type": "User"
                                    }
                                },
                                "requestedTo": {
                                    "data": {
                                        "id": "80102512345",
                                        "type": "User"
                                    }
                                }
                            },
                            "meta": {
                                "contract": "value1"
                            }
                        },
                        {
                            "id": "4f71d596-99e4-415e-946d-7252c1a40c5b",
                            "type": "AuthorizationRequest",
                            "attributes": {
                                "status": "Accepted",
                                "validTo": "2025-04-04T02:00"
                            },
                            "relationships": {
                                "requestedBy": {
                                    "data": {
                                        "id": "0847976000005",
                                        "type": "User"
                                    }
                                },
                                "requestedTo": {
                                    "data": {
                                        "id": "80102512345",
                                        "type": "User"
                                    }
                                }
                            },
                            "meta": {
                                "contract": "value2"
                            }
                        }
                    ],
                    "links":{"self":"http://localhost/authorization-requests"}
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }
    }

    describe("GET $AUTHORIZATION_REQUEST/{id}") {

        it("should return 200 OK on a valid ID") {
            val response = testApp.client.get("$AUTHORIZATION_REQUEST/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6")
            response.status shouldBe HttpStatusCode.OK

            val responseString = response.bodyAsText()
            responseString.shouldBeValidJson()

            val expectedJson = """
                {
                    "data": {
                        "id": "d81e5bf2-8a0c-4348-a788-2a3fab4e77d6",
                        "type": "AuthorizationRequest",
                        "attributes": {
                            "status": "Pending",
                            "validTo": "2025-04-04T02:00"
                        },
                        "relationships": {
                            "requestedBy": {
                                "data": {
                                    "id": "0847976000005",
                                    "type": "User"
                                }
                            },
                            "requestedTo": {
                                "data": {
                                    "id": "80102512345",
                                    "type": "User"
                                }
                            }
                        },
                        "meta": {
                            "contract": "value1"
                        }
                    },
                    "links":{"self":"http://localhost/authorization-requests/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6"}
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }

        it("should return 400 Bad Request on an invalid ID format") {
            val response = testApp.client.get("$AUTHORIZATION_REQUEST/invalid-id")
            response.status shouldBe HttpStatusCode.BadRequest

            val responseString = response.bodyAsText()
            responseString.shouldBeValidJson()

            val expectedJson = """
                {
                    "errors": [
                        {
                            "status": 400,
                            "title": "Bad Request",
                            "detail": "Missing or malformed id."
                        }
                    ],
                    "links":{
                        "self":"http://localhost/authorization-requests/invalid-id"
                    }
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }

        it("should return 404 Not Found on an invalid ID that is a UUID") {
            val response = testApp.client.get("$AUTHORIZATION_REQUEST/167b1be9-f563-4b31-af1a-50439d567ee5")
            response.status shouldBe HttpStatusCode.NotFound

            val responseString = response.bodyAsText()
            responseString.shouldBeValidJson()

            val expectedJson = """
                {
                    "errors": [
                        {
                            "status": 404,
                            "title": "Not Found",
                            "detail": "Could not find AuthorizationRequest with id 167b1be9-f563-4b31-af1a-50439d567ee5."
                        }
                    ],
                    "links":{
                        "self":"http://localhost/authorization-requests/167b1be9-f563-4b31-af1a-50439d567ee5"
                    }
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }
    }

    // POST /authorization-requests
    describe("POST $AUTHORIZATION_REQUEST") {

        it("should return 201 Created") {
            val requestBody = """
                {
                    "data": {
                        "type": "AuthorizationRequest",
                        "attributes": {
                            "requestType": "ChangeOfSupplierConfirmation"
                        },
                        "relationships": {
                            "requestedBy": {
                                "data": {
                                    "id": "0847976000005",
                                    "type": "Organization"
                                }
                            },
                            "requestedTo": {
                                "data": {
                                    "id": "80102512345",
                                    "type": "Person"
                                }
                            }
                        },
                        "meta": {
                            "contract": "SampleContract"
                        }
                    }
                }
            """.trimIndent()
            val response = testApp.client.post(AUTHORIZATION_REQUEST) {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
            }
            response.status shouldBe HttpStatusCode.Created

            val responseString = response.bodyAsText()
            val expectedJson = """
                {
                    "data": {
                        "type": "AuthorizationRequest",
                        "attributes": {
                            "status": "Pending"
                        },
                        "relationships": {
                            "requestedBy": {
                                "data": {
                                    "id": "0847976000005",
                                    "type": "User"
                                }
                            },
                            "requestedTo": {
                                "data": {
                                    "id": "80102512345",
                                    "type": "User"
                                }
                            }
                        },
                        "meta": {
                            "contract": "SampleContract"
                        }
                    }
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }

        // This passes because the JSON serializer is lenient and allows for extra fields.
        // Switch on when stricter validation is added (e.g., schema validation).
        xit("should return 400 Bad Request if the requestBody is invalid (added attribute)") {
            val requestBody = """
                {
                    "type": "AuthorizationRequest",
                    "data": {
                        "type": "AuthorizationRequest",
                        "attributes": {
                            "requestType": "ChangeOfSupplierConfirmation"
                        },
                        "relationships": {
                            "requestedBy": {
                                "data": {
                                    "id": "0847976000005",
                                    "type": "Organization"
                                }
                            },
                            "requestedTo": {
                                "data": {
                                    "id": "80102512345",
                                    "type": "Person"
                                }
                            }
                        },
                        "meta": {
                            "contract": "SampleContract"
                        }
                    }
                }
            """.trimIndent()
            val response = testApp.client.post(AUTHORIZATION_REQUEST) {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
            }
            response.status shouldBe HttpStatusCode.BadRequest

            val responseString = response.bodyAsText()
            val expectedJson = """
                {
                    "errors":[
                        {
                            "status":400,
                            "title":"Bad Request",
                            "detail":"Missing or malformed requestBody in POST call."
                        }
                    ],
                    "links":{
                        "self":"http://localhost/authorization-requests"
                    }
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }

        it("should return 400 Bad Request if the requestBody is invalid (missing attribute)") {
            val requestBody = """
                {
                    "data": {
                        "attributes": {
                            "requestType": "ChangeOfSupplierConfirmation"
                        },
                        "relationships": {
                            "requestedBy": {
                                "data": {
                                    "id": "0847976000005",
                                    "type": "Organization"
                                }
                            },
                            "requestedTo": {
                                "data": {
                                    "id": "80102512345",
                                    "type": "Person"
                                }
                            }
                        },
                        "meta": {
                            "contract": "SampleContract"
                        }
                    }
                }
            """.trimIndent()
            val response = testApp.client.post(AUTHORIZATION_REQUEST) {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
            }
            response.status shouldBe HttpStatusCode.BadRequest

            val responseString = response.bodyAsText()
            val expectedJson = """
                {
                    "errors":[
                        {
                            "status":400,
                            "title":"Bad Request",
                            "detail": "Missing or malformed requestBody in POST call."
                        }
                    ],
                    "links":{
                        "self":"http://localhost/authorization-requests"
                    }
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }

        it("should return 400 Bad Request if the requestType is invalid") {
            val requestBody = """
                {
                    "data": {
                        "type": "AuthorizationRequest",
                        "attributes": {
                            "requestType": "InvalidRequestType"
                        },
                        "relationships": {
                            "requestedBy": {
                                "data": {
                                    "id": "0847976000005",
                                    "type": "Organization"
                                }
                            },
                            "requestedTo": {
                                "data": {
                                    "id": "80102512345",
                                    "type": "Person"
                                }
                            }
                        },
                        "meta": {
                            "contract": "SampleContract"
                        }
                    }
                }
            """.trimIndent()
            val response = testApp.client.post(AUTHORIZATION_REQUEST) {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
            }
            response.status shouldBe HttpStatusCode.BadRequest

            val responseString = response.bodyAsText()
            val expectedJson = """
                {
                    "errors":[
                        {
                            "status":400,
                            "title":"Bad Request",
                            "detail": "Invalid requestType: InvalidRequestType."
                        }
                    ],
                    "links":{
                        "self":"http://localhost/authorization-requests"
                    }
                }
            """.trimIndent()
            responseString shouldEqualSpecifiedJson expectedJson
        }
    }
})
