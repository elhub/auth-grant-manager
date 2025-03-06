package no.elhub.devxp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.elhub.devxp.config.configureDatabase
import no.elhub.devxp.config.configureKoin
import no.elhub.devxp.config.configureLogging
import no.elhub.devxp.config.configureMonitoring
import no.elhub.devxp.config.configureRouting
import no.elhub.devxp.config.configureSecurity
import no.elhub.devxp.config.configureSerialization

class ApplicationIntegrationTest : DescribeSpec({

    describe("Ping service") {
        it("should respond with 'pong'") {
            testApplication {
                application {
                    configureKoin()
                    configureLogging()
                    configureMonitoring()
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

    describe("Authorization Grant service") {
        it("should respond with a grant") {
            testApplication {
                application {
                    configureDatabase()
                    configureKoin()
                    configureLogging()
                    configureMonitoring()
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val response = client.get("/authorization-grant")
                response.status shouldBe HttpStatusCode.OK
                val grantId = response.bodyAsText()
                databaseHasGrant(grantId) shouldBe true
            }
        }
    }

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
})
