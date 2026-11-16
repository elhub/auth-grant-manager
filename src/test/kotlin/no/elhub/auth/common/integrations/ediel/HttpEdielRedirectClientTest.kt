package no.elhub.auth.common.integrations.ediel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import java.util.Base64

class HttpEdielRedirectClientTest : FunSpec({
    val config = EdielConfig(
        serviceUrl = "https://ediel.example/api/",
        username = "client",
        password = "secret",
        environment = EdielEnvironment.PRODUCTION,
    )

    test("requests a party redirect using basic authentication") {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    request.url.fullPath shouldBe "/api/PartyRedirectUrl?gln=7080000000000"
                    request.headers[HttpHeaders.Authorization] shouldBe
                        "Basic ${Base64.getEncoder().encodeToString("client:secret".toByteArray())}"
                    request.headers[HttpHeaders.Accept] shouldBe "application/json"
                    respond(
                        content = """{"RedirectUrls":{"Production":"https://example.com","Test":"https://test.example.com"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

        val redirectUri = HttpEdielRedirectClient(config, client).getPartyRedirectUri("7080000000000")

        redirectUri shouldBe "https://example.com"
        client.close()
    }

    test("selects the test redirect URI when configured for test") {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"RedirectUrls":{"Production":"https://example.com","Test":"https://test.example.com"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

        val testConfig = config.copy(environment = EdielEnvironment.TEST)
        HttpEdielRedirectClient(testConfig, client).getPartyRedirectUri("7080000000000") shouldBe "https://test.example.com"
        client.close()
    }

    test("reports a missing party redirect distinctly") {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("", status = HttpStatusCode.NotFound) } }
        }

        shouldThrow<EdielPartyRedirectNotFoundException> {
            HttpEdielRedirectClient(config, client).getPartyRedirectUri("7080000000000")
        }
        client.close()
    }

    test("reports non-success responses as Ediel request failures") {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("", status = HttpStatusCode.ServiceUnavailable) } }
        }

        shouldThrow<EdielRedirectRequestException> {
            HttpEdielRedirectClient(config, client).getPartyRedirectUri("7080000000000")
        }
        client.close()
    }
})
