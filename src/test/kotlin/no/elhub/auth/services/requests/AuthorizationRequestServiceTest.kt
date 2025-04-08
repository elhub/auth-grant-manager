package no.elhub.auth.services.requests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.config.AUTHORIZATION_REQUEST
import no.elhub.auth.utils.defaultTestApplication

class AuthorizationRequestServiceTest : DescribeSpec({

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("AuthorizationRequestService") {

        xit("get should return requests with status OK") {
            val response = testApp.client.get(AUTHORIZATION_REQUEST)
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("data") shouldBe true
        }

        it("get on specific id should return with status OK and correct data") {
            val id = "d81e5bf2-8a0c-4348-a788-2a3fab4e77d6"
            val response = testApp.client.get("$AUTHORIZATION_REQUEST/$id")
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Assert the "data" object exists
            val data = responseBody["data"]?.jsonObject
            data shouldNotBe null

            // Assert the "id" and "type" fields
            data!!["id"]?.jsonPrimitive?.content shouldBe id
            data["type"]?.jsonPrimitive?.content shouldBe "AuthorizationRequest"

            // Assert the "attributes" object
            val attributes = data["attributes"]?.jsonObject
            attributes shouldNotBe null
            attributes!!["status"]?.jsonPrimitive?.content shouldBe "Pending"
            attributes["createdAt"]?.jsonPrimitive?.content shouldNotBe null
            attributes["updatedAt"]?.jsonPrimitive?.content shouldNotBe null
            attributes["validTo"]?.jsonPrimitive?.content shouldBe "2025-04-04T02:00"

            // Assert the "relationships" object
            val relationships = data["relationships"]?.jsonObject
            relationships shouldNotBe null

            val requestedBy = relationships!!["requestedBy"]?.jsonObject?.get("data")?.jsonObject
            requestedBy shouldNotBe null
            requestedBy!!["id"]?.jsonPrimitive?.content shouldBe "0847976000005"
            requestedBy["type"]?.jsonPrimitive?.content shouldBe "User"

            val requestedTo = relationships["requestedTo"]?.jsonObject?.get("data")?.jsonObject
            requestedTo shouldNotBe null
            requestedTo!!["id"]?.jsonPrimitive?.content shouldBe "80102512345"
            requestedTo["type"]?.jsonPrimitive?.content shouldBe "User"

            // Assert the "meta" object
            val meta = responseBody["meta"]?.jsonObject
            meta shouldNotBe null
            meta!!["createdAt"]?.jsonPrimitive?.content shouldNotBe null

            // Assert the "links" object
            val links = responseBody["links"]?.jsonObject
            links shouldNotBe null
            links!!["self"]?.jsonPrimitive?.content shouldBe "http://localhost/authorization-requests/$id"
        }

        it("post should create request with ID") {
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

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Assert the "data" object exists
            val data = responseBody["data"]?.jsonObject
            data shouldNotBe null

            // Assert the "id" and "type" fields
            data!!["id"]?.jsonPrimitive?.content shouldNotBe null
            data["type"]?.jsonPrimitive?.content shouldBe "AuthorizationRequest"

            // Assert the "attributes" object
            val attributes = data["attributes"]?.jsonObject
            attributes shouldNotBe null
            attributes!!["status"]?.jsonPrimitive?.content shouldBe "Pending"
            attributes["createdAt"]?.jsonPrimitive?.content shouldNotBe null
            attributes["updatedAt"]?.jsonPrimitive?.content shouldNotBe null
            attributes["validTo"]?.jsonPrimitive?.content shouldNotBe null

            // Assert the "relationships" object
            val relationships = data["relationships"]?.jsonObject
            relationships shouldNotBe null

            val requestedBy = relationships!!["requestedBy"]?.jsonObject?.get("data")?.jsonObject
            requestedBy shouldNotBe null
            requestedBy!!["id"]?.jsonPrimitive?.content shouldBe "0847976000005"
            requestedBy["type"]?.jsonPrimitive?.content shouldBe "User"

            val requestedTo = relationships["requestedTo"]?.jsonObject?.get("data")?.jsonObject
            requestedTo shouldNotBe null
            requestedTo!!["id"]?.jsonPrimitive?.content shouldBe "80102512345"
            requestedTo["type"]?.jsonPrimitive?.content shouldBe "User"

            // Assert the "meta" object
            val meta = responseBody["meta"]?.jsonObject
            meta shouldNotBe null
            meta!!["createdAt"]?.jsonPrimitive?.content shouldNotBe null

            // Assert the "links" object
            val links = responseBody["links"]?.jsonObject
            links shouldNotBe null
        }

    }
})
