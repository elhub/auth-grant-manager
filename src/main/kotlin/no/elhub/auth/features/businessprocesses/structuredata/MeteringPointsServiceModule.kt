package no.elhub.auth.features.businessprocesses.structuredata

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.ktor.plugin.koinModule

fun Application.meteringPointsServiceModule() {
    koinModule {
        single { environment.config }
        single {
            val meteringPointsApiConfig = get<ApplicationConfig>().config("structureData.meteringPointsService")
            MeteringPointsApiConfig(
                serviceUrl = meteringPointsApiConfig.property("serviceUrl").getString(),
                basicAuthConfig = BasicAuthConfig(
                    username = meteringPointsApiConfig.property("authentication.basic.username").getString(),
                    password = meteringPointsApiConfig.property("authentication.basic.password").getString()
                )
            )
        }

        single(named("meteringPointsHttpClient")) {
            val meteringPointsApiConfig = get<MeteringPointsApiConfig>()
            val basicAuthUsername = meteringPointsApiConfig.basicAuthConfig.username
            val basicAuthPassword = meteringPointsApiConfig.basicAuthConfig.password

            HttpClient(CIO) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 40_000
                }
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(basicAuthUsername, basicAuthPassword)
                        }
                        sendWithoutRequest { true }
                        realm = "Access to the '/' path"
                    }
                }

                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                        },
                        contentType = ContentType.Application.Json,
                    )
                }

                install(UserAgent) { agent = "auth-grant-manager" }
            }
        }

        single<MeteringPointsService> {
            MeteringPointsApi(
                meteringPointsApiConfig = get(),
                client = get(named("meteringPointsHttpClient"))
            )
        }
    }
}
