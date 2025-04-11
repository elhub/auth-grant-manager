package no.elhub.auth

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import no.elhub.auth.utils.defaultTestApplication

class ApplicationTest : DescribeSpec({

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("Application") {
        xit("should return /health OK") {
            val response = testApp.client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "OK"
        }
    }
})
