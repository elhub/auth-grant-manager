package no.elhub.auth.features.grants

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.DatabaseExtension
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.utils.defaultTestApplication

class AuthorizationGrantHandlerTest :
    DescribeSpec({
        extensions(DatabaseExtension)

        lateinit var testApp: TestApplication

        beforeTest {
            testApp = defaultTestApplication()
        }

        afterTest {
            testApp.stop()
        }

        describe("AuthorizationGrantService") {

            it("should return grants with status OK") {
                val response = testApp.client.get(AUTHORIZATION_GRANT)
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }

            it("should return grant by id with status OK") {
                val response = testApp.client.get("$AUTHORIZATION_GRANT/123e4567-e89b-12d3-a456-426614174000")
                response.status shouldBe HttpStatusCode.OK

                val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                responseBody.containsKey("meta") shouldBe true
            }
        }
    })
