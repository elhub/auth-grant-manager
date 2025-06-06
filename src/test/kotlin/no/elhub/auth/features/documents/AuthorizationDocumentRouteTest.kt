package no.elhub.auth.features.documents

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.extensions.PostgresTestContainerExtension
import no.elhub.auth.config.AUTHORIZATION_DOCUMENT
import no.elhub.auth.utils.defaultTestApplication
import no.elhub.auth.validate

class AuthorizationDocumentRouteTest : DescribeSpec({
    extensions(PostgresTestContainerExtension)

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("POST /authorization-documents") {
        it("should return 201 OK with correct response when request is valid") {

            val response = testApp.client
                .post(AUTHORIZATION_DOCUMENT) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "data": {
                                "type": "AuthorizationDocument",
                                "attributes": {
                                    "meteringPoint": "1234"
                                },
                                "relationships": {
                                    "requestedBy": {
                                        "data": {
                                            "id": "12345678901",
                                            "type": "User"
                                        }
                                    },
                                    "requestedTo": {
                                        "data": {
                                            "id": "98765432109",
                                            "type": "User"
                                        }
                                    }
                                }

                            }
                        }
                        """.trimIndent()
                    )
                }

            response.status shouldBe HttpStatusCode.Created

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.validate {
                "data" {
                    "type" shouldBe "AuthorizationDocument"
                    "id".shouldNotBeNull()
                    "attributes" {
                        "createdAt".shouldNotBeNull()
                        "updatedAt".shouldNotBeNull()
                        "status" shouldBe "Pending"
                    }
                    "relationships" {
                        "requestedBy" {
                            "data" {
                                "id" shouldBe "12345678901"
                                "type" shouldBe "User"
                            }
                        }
                        "requestedTo" {
                            "data" {
                                "id" shouldBe "98765432109"
                                "type" shouldBe "User"
                            }
                        }
                    }
                }
            }
        }
    }
})
