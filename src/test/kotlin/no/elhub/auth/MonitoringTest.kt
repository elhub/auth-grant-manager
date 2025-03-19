package no.elhub.auth

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureMonitoring
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization

class MonitoringTest : DescribeSpec({

    describe("Application monitoring") {

        it("should return ok when /metrics is called") {
            testApplication {
                application {
                    val dataSource = configureDatabase()
                    configureKoin()
                    configureLogging()
                    configureMonitoring(dataSource)
                    configureSerialization()
                    configureSecurity()
                    configureRouting()
                }

                val response = client.get("/metrics")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
})
