package no.elhub.auth.services.requests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.config.AUTHORIZATION_REQUEST
import no.elhub.auth.utils.defaultTestApplication
import java.util.*

class AuthorizationRequestServiceTest : DescribeSpec({

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("AuthorizationRequestService") {

        it("get should return requests with status OK") {
            val response = testApp.client.get(AUTHORIZATION_REQUEST)
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }

        it("post should create request with ID") {
            val response = testApp.client.post(AUTHORIZATION_REQUEST) {}
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }

        it("should return request by id with status OK") {
            val id = UUID.randomUUID().toString()
            val response = testApp.client.get("$AUTHORIZATION_REQUEST/$id")
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }
    }
})
