package no.elhub.auth.features.businessprocesses.ediel

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError

class EdielApiTest : FunSpec({
    val gln = "9301000000023"
    val serviceUrl = "http://localhost:8080"
    val username = "ediel-user"
    val password = "ediel-pass"
    val config = EdielApiConfig(
        serviceUrl = serviceUrl,
        basicAuthConfig = BasicAuthConfig(
            username = username,
            password = password
        )
    )

    val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                request.headers[HttpHeaders.Authorization] shouldBe "Basic ZWRpZWwtdXNlcjplZGllbC1wYXNz"
                when (request.url.fullPath) {
                    "/PartyRedirectUrl/?gln=$gln" -> respond(
                        content = "Unauthorized",
                        status = HttpStatusCode.Unauthorized
                    )

                    else -> respond(
                        content = "Not Found",
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }
    }

    val service = EdielApi(config, client)

    test("Should return unauthorized when EDIEL returns 401 without JSON:API error payload") {
        val response = service.getPartyRedirect(gln)
        response.shouldBeLeft()
        response.value shouldBe ClientError.Unauthorized
    }
})
