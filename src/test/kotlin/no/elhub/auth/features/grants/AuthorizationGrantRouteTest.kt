package no.elhub.auth.features.grants

import io.kotest.assertions.json.shouldBeValidJson
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import no.elhub.auth.DatabaseExtension
import no.elhub.auth.config.AUTHORIZATION_GRANT
import no.elhub.auth.utils.defaultTestApplication
import java.nio.file.Files
import java.nio.file.Paths

class AuthorizationGrantRouteTest : DescribeSpec({
    extensions(DatabaseExtension)

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("GET /authorization-grants") {
        it("should return 200 OK") {
            val response = testApp.client.get(AUTHORIZATION_GRANT)
            response.status shouldBe HttpStatusCode.OK

            val responseString = response.bodyAsText()
            responseString.shouldBeValidJson()

            val expectedJson = Files.readString(Paths.get("src/test/resources/grants/authorization-grant-get-response-data.json"))

            responseString shouldEqualSpecifiedJson expectedJson
        }
    }
})
