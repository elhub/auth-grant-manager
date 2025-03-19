package no.elhub.auth.services.grants

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization
import java.util.UUID

class AuthorizationGrantServiceTest : DescribeSpec({

    describe("AuthorizationGrantService") {

        it("should return grants with status OK") {
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
                val response = client.get(AUTHORIZATION_GRANT)
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }
        }

        it("should return grant by id with status OK") {
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
                val response = client.get("$AUTHORIZATION_GRANT/$id")
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }
        }
    }
})
