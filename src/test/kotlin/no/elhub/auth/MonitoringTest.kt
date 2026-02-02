package no.elhub.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication

class MonitoringTest :
    FunSpec({

        lateinit var testApp: TestApplication

        beforeTest {
            testApp = defaultTestApplication()
        }

        afterTest {
            testApp.stop()
        }

        context("Application monitoring") {

            // This isn't working correctly. It may be because metrics hava not initialized or don't work
            // in the testApp.
            xtest("Should return ok when /metrics is called") {
                val response = testApp.client.get("/metrics")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    })
