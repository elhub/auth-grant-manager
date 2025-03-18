package no.elhub.auth

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization

class ApplicationTest : DescribeSpec({

    describe("Application") {
        it("should return /health OK") {
            testApplication {
                application {
                    configureDatabase()
                    configureKoin()
                    configureLogging()
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val response = client.get("/health")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "OK"
            }
        }
    }
})
