package no.elhub.devxp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ApplicationIntegrationTest : DescribeSpec({
    describe("Application and Database communication") {
        it("should respond with 'pong' from the Ping service") {
            testApplication {
                val response = client.get("/ping")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "pong"
            }
        }

        it("should respond with 'OK' from the Health endpoint") {
            testApplication {
                val response = client.get("/health")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "OK"
            }
        }

        it("should communicate with the database") {
            testApplication {
                // Add your logic to test database communication here
                // For example, you can create a new record and verify it
                val response = client.get("/consent-request")
                response.status shouldBe HttpStatusCode.OK
                // Add more assertions based on your application's logic
            }
        }
    }
})
