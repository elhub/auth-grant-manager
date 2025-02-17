package no.elhub.devxp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.elhub.devxp.config.configureMonitoring

class ApplicationTest : DescribeSpec({
    describe("The testApplication") {

        it("should return OK status for /metrics endpoint when configured for monitoring") {
            testApplication {
                application {
                    configureMonitoring()
                }

                client.get("/metrics").apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText().contains("# TYPE") shouldBe true
                }
            }
        }
    }
})
