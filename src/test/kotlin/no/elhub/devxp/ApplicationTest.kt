package no.elhub.devxp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class PingServiceIntegrationTest : DescribeSpec({
    describe("Ping service") {
        it("should respond with 'pong'") {
            testApplication {
                val response = client.get("/ping")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "pong"
            }
        }
    }
})
