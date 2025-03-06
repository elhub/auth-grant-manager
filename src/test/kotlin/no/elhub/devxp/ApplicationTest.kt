package no.elhub.devxp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.elhub.devxp.config.configureKoin
import no.elhub.devxp.config.configureLogging
import no.elhub.devxp.config.configureRouting
import no.elhub.devxp.config.configureSecurity
import no.elhub.devxp.config.configureSerialization

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
