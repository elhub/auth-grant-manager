package no.elhub.auth.v1.integrations.pdp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v1.common.AuthorizationRulesService

class AuthorizationRulesServiceModuleTest : FunSpec({
    test("provides a PDP token provider with its own credentials and cached token") {
        var tokenRequests = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                tokenRequests++
                request.url.toString() shouldBe "https://idp.example/token"
                val form = (request.body as FormDataContent).formData
                form["grant_type"] shouldBe "client_credentials"
                form["client_id"] shouldBe "pdp-client"
                form["client_secret"] shouldBe "pdp-secret"
                respond(
                    """{"access_token":"pdp-token","expires_in":300}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        ) {
            install(ContentNegotiation) {
                json()
            }
        }

        try {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "pdp.baseUrl" to "https://pdp.example",
                        "pdp.idp.clientId" to "pdp-client",
                        "pdp.idp.clientSecret" to "pdp-secret",
                        "idp.tokenUrl" to "https://idp.example/token",
                    )
                }
                application {
                    install(DI)
                    val appConfig = environment.config
                    dependencies {
                        provide<ApplicationConfig> { appConfig }
                        provide<HttpClient>(name = "commonHttpClient") { httpClient }
                    }
                    authorizationRulesServiceModule()
                    routing {
                        get("/token") {
                            dependencies.resolve<AuthorizationRulesService>()
                                .shouldBeInstanceOf<PdpAuthorizationRulesService>()
                            val provider = dependencies.resolve<JwtTokenProvider>("pdpServiceTokenProvider")
                            call.respondText(provider.getToken())
                        }
                    }
                }

                client.get("/token").bodyAsText() shouldBe "pdp-token"
                client.get("/token").bodyAsText() shouldBe "pdp-token"
                tokenRequests shouldBe 1
            }
        } finally {
            httpClient.close()
        }
    }
})
