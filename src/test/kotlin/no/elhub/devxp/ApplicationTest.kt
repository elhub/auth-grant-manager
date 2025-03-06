package no.elhub.devxp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.elhub.devxp.config.*

class ApplicationTest : DescribeSpec({

    describe("Ping service") {
        it("should respond with 'pong'") {
            testApplication {
                application {
                    // Ignore the database
                    configureKoin()
                    configureLogging()
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val response = client.get("/ping")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "pong"
            }
        }
    }

})
