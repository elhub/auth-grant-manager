package no.elhub.auth.services.requests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.config.AUTHORIZATION_REQUEST
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization
import java.util.UUID

class AuthorizationRequestServiceTest : DescribeSpec({

    describe("AuthorizationRequestService") {

        it("get should return requests with status OK") {
            testApplication {
                application {
                    configureDatabase()
                    configureKoin()
                    configureLogging()
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val client = createClient { }
                val response = client.get(AUTHORIZATION_REQUEST)
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }
        }

        it("post should create request with ID") {
            testApplication {
                application {
                    configureDatabase()
                    configureKoin()
                    configureLogging()
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val client = createClient { }
                val response = client.post(AUTHORIZATION_REQUEST) {}
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }
        }

        it("should return request by id with status OK") {
            testApplication {
                application {
                    configureDatabase()
                    configureKoin()
                    configureLogging()
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val client = createClient { }
                val id = UUID.randomUUID().toString()
                val response = client.get("$AUTHORIZATION_REQUEST/$id")
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }
        }
    }
})
