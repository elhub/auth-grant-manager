package no.elhub.auth.grantmanager.presentation.features.requests

import io.kotest.assertions.json.shouldBeValidJson
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplication
import no.elhub.auth.grantmanager.presentation.config.AUTHORIZATION_REQUEST
import no.elhub.auth.grantmanager.presentation.extensions.PostgresTestContainerExtension
import no.elhub.auth.grantmanager.presentation.extensions.RunPostgresScriptExtension
import no.elhub.auth.grantmanager.presentation.utils.defaultTestApplication
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Paths

class AuthorizationRequestRouteTest :
    DescribeSpec({
        extensions(PostgresTestContainerExtension)
        extensions(RunPostgresScriptExtension(scriptResourcePath = "db/insert-authorization-requests.sql"))

        lateinit var testApp: TestApplication

        beforeSpec {
            testApp = defaultTestApplication()
        }

        afterSpec {
            testApp.stop()
        }

        // GET /authorization-requests
        describe("GET $AUTHORIZATION_REQUEST") {

            it("should return 200 OK") {
                val response = testApp.client.get(AUTHORIZATION_REQUEST)
                response.status shouldBe HttpStatusCode.OK

                val responseString = response.bodyAsText()
                responseString.shouldBeValidJson()

                val expectedJson = Files.readString(Paths.get("src/test/resources/requests/authorization-request-get-response-data.json"))

                responseString shouldEqualSpecifiedJson expectedJson
            }
        }

        describe("GET $AUTHORIZATION_REQUEST/{id}") {

            it("should return 200 OK on a valid ID") {
                val response = testApp.client.get("$AUTHORIZATION_REQUEST/d81e5bf2-8a0c-4348-a788-2a3fab4e77d6")
                response.status shouldBe HttpStatusCode.OK

                val responseString = response.bodyAsText()
                responseString.shouldBeValidJson()

                val expectedJson = Files.readString(Paths.get("src/test/resources/requests/authorization-request-get-id-response-data.json"))

                responseString shouldEqualSpecifiedJson expectedJson
            }

            it("should return 400 Bad Request on an invalid ID format") {
                val response = testApp.client.get("$AUTHORIZATION_REQUEST/invalid-id")
                response.status shouldBe HttpStatusCode.BadRequest

                val responseString = response.bodyAsText()
                responseString.shouldBeValidJson()

                val expectedJson =
                    """
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

                val expectedJson =
                    """
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

                val requestBody = Files.readString(Paths.get("src/test/resources/requests/authorization-request-post-request-data.json"))

                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }

                response.status shouldBe HttpStatusCode.Created

                val responseString = response.bodyAsText()

                // The "id" field in the response is dynamically generated by the server. Since the value of "id" cannot be predicted, we extract it from the
                // actual response and replace the placeholder "id" in the expected JSON with the dynamically generated value.
                // This ensures that the test can validate the rest of the response while accommodating the dynamic "id"
                val responseJson = ObjectMapper().readTree(responseString)
                val generatedId = responseJson["data"]["id"].asText()

                val expectedJson = Files.readString(Paths.get("src/test/resources/requests/authorization-request-post-response-data.json"))
                val expectedJsonWithDynamicId = expectedJson.replace("\"id\": \"123e4567-e89b-12d3-a456-426614174000\"", "\"id\": \"$generatedId\"")

                responseString shouldEqualSpecifiedJson expectedJsonWithDynamicId
            }

            // This passes because the JSON serializer is lenient and allows for extra fields.
            // Switch on when stricter validation is added (e.g., schema validation).
            xit("should return 400 Bad Request if the requestBody is invalid (added attribute)") {
                val requestBody =
                    """
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
                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                response.status shouldBe HttpStatusCode.BadRequest

                val responseString = response.bodyAsText()
                val expectedJson =
                    """
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
                val requestBody =
                    """
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
                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                response.status shouldBe HttpStatusCode.BadRequest

                val responseString = response.bodyAsText()
                val expectedJson =
                    """
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
                val requestBody =
                    """
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
                val response =
                    testApp.client.post(AUTHORIZATION_REQUEST) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                response.status shouldBe HttpStatusCode.BadRequest

                val responseString = response.bodyAsText()
                val expectedJson =
                    """
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
