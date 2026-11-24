package no.elhub.auth.common.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.configureErrorHandling
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

class SerializationTest : FunSpec({
    test("v1 uses JSON:API by default and negotiates plain JSON for legacy clients") {
        testApplication {
            application {
                configureSerialization()
                configureErrorHandling()
                routing {
                    get("/access/v0/test") { call.respond(buildJsonObject { put("data", "example") }) }
                    get("/access/v0/missing") { throw RepositoryReadError.NotFoundError("Missing document") }
                    get("/access/v1/test") { call.respond(buildJsonObject { put("data", "example") }) }
                    get("/access/v1/missing.pdf") { throw RepositoryReadError.NotFoundError("Missing document") }
                }
            }

            suspend fun getV1(accept: String? = null, path: String = "test") = client.get("/access/v1/$path") {
                if (accept != null) header(HttpHeaders.Accept, accept)
            }

            client.get("/access/v0/test").contentType() shouldBe ContentType.Application.Json
            val v0Error = client.get("/access/v0/missing") { header(HttpHeaders.Accept, "application/json") }
            v0Error.status shouldBe HttpStatusCode.NotFound
            v0Error.contentType() shouldBe jsonApiContentType
            getV1().contentType() shouldBe jsonApiContentType
            getV1("*/*").contentType() shouldBe jsonApiContentType
            getV1("application/json;q=0, */*").contentType() shouldBe jsonApiContentType
            getV1("application/vnd.api+json;profile=\"https://example.com/profile\"").contentType() shouldBe jsonApiContentType
            getV1("application/vnd.api+json;ext=\"https://example.com/extension\"").status shouldBe HttpStatusCode.NotAcceptable
            val notAcceptable = getV1("application/json;q=0")
            notAcceptable.status shouldBe HttpStatusCode.NotAcceptable
            notAcceptable.contentType() shouldBe jsonApiContentType
            notAcceptable.headers[HttpHeaders.Vary] shouldBe HttpHeaders.Accept
            val error = Json.decodeFromString<JsonApiErrorCollection>(notAcceptable.bodyAsText()).errors.single()
            error.status shouldBe "406"
            error.title shouldBe "Not acceptable"

            val legacyResponse = getV1("application/json")
            legacyResponse.status shouldBe HttpStatusCode.OK
            legacyResponse.contentType() shouldBe ContentType.Application.Json
            legacyResponse.headers[HttpHeaders.Vary] shouldBe HttpHeaders.Accept

            val pdfError = getV1("application/pdf", path = "missing.pdf")
            pdfError.status shouldBe HttpStatusCode.NotFound
            pdfError.contentType() shouldBe jsonApiContentType
            val legacyError = getV1("application/pdf, application/json", path = "missing.pdf")
            legacyError.status shouldBe HttpStatusCode.NotFound
            legacyError.contentType() shouldBe ContentType.Application.Json
            legacyError.bodyAsText().contains("Not found error") shouldBe true
        }
    }
})
