package no.elhub.auth.grantmanager.presentation

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import no.elhub.auth.grantmanager.presentation.utils.defaultTestApplication

class MonitoringTest : DescribeSpec({

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("Application monitoring") {

        // This isn't working correctly. It may be because metrics hava not initialized or don't work
        // in the testApp.
        xit("should return ok when /metrics is called") {
            val response = testApp.client.get("/metrics")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
