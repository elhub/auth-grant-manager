package no.elhub.auth.services.documents

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.elhub.auth.config.AUTHORIZATION_DOCUMENT
import no.elhub.auth.utils.defaultTestApplication

class AuthorizationDocumentServiceTest : DescribeSpec({

    lateinit var testApp: TestApplication

    beforeTest {
        testApp = defaultTestApplication()
    }

    afterTest {
        testApp.stop()
    }

    describe("AuthorizationDocumentService") {

        it("get should return documents with status OK") {
            val response = testApp.client.get(AUTHORIZATION_DOCUMENT)
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }

        it("post should create document with ID") {
            val response = testApp.client.get(AUTHORIZATION_DOCUMENT)
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }

        it("should return document by id with status OK") {
            val response = testApp.client.get(AUTHORIZATION_DOCUMENT)
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }

        it("should update the document when patched") {
            val response = testApp.client.get(AUTHORIZATION_DOCUMENT)
            response.status shouldBe HttpStatusCode.OK

            val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseBody.containsKey("meta") shouldBe true
        }
    }
})
