package no.elhub.auth.features.businessprocesses.structuredata.organisations

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
import io.ktor.server.plugins.di.dependencies
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

fun Application.organisationsServiceModule() {
    dependencies {
        provide<OrganisationsApiConfig> {
            val organisationsApiConfig = resolve<ApplicationConfig>().config("structureData.organisationsService")
            OrganisationsApiConfig(
                serviceUrl = organisationsApiConfig.property("serviceUrl").getString(),
                basicAuthConfig = BasicAuthConfig(
                    username = organisationsApiConfig.property("authentication.basic.username").getString(),
                    password = organisationsApiConfig.property("authentication.basic.password").getString()
                )
            )
        }

        provide<HttpClient>(name = "organisationsHttpClient") {
            val organisationsApiConfig = resolve<OrganisationsApiConfig>()
            val basicAuthUsername = organisationsApiConfig.basicAuthConfig.username
            val basicAuthPassword = organisationsApiConfig.basicAuthConfig.password

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

        provide<OrganisationsService> {
            OrganisationsApi(
                organisationsApiConfig = resolve(),
                client = resolve("organisationsHttpClient")
            )
        }
    }
}
