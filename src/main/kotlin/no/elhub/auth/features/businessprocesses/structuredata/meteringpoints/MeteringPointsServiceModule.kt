package no.elhub.auth.features.businessprocesses.structuredata.meteringpoints

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
import io.ktor.server.plugins.di.resolve
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

fun Application.meteringPointsServiceModule() {


    dependencies {

        provide<MeteringPointsApiConfig> {

            val meteringPointsApiConfig = resolve<ApplicationConfig>().config("structureData.meteringPointsService")
            MeteringPointsApiConfig(
                serviceUrl = meteringPointsApiConfig.property("serviceUrl").getString(),
                basicAuthConfig = BasicAuthConfig(
                    username = meteringPointsApiConfig.property("authentication.basic.username").getString(),
                    password = meteringPointsApiConfig.property("authentication.basic.password").getString()
                )
            )
        }
        provide(name = "meteringPointsHttpClient") {
            val meteringPointsApiConfig = resolve<MeteringPointsApiConfig>()
            val basicAuthUsername = meteringPointsApiConfig.basicAuthConfig.username
            val basicAuthPassword = meteringPointsApiConfig.basicAuthConfig.password

            HttpClient(CIO) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 40_000
                }
                engine {
                    https {
                        trustManager = object: X509TrustManager {
                            override fun checkClientTrusted(p0: Array<out X509Certificate?>?, p1: String?) {
                                return Unit
                            }

                            override fun checkServerTrusted(p0: Array<out X509Certificate?>?, p1: String?) {
                                return Unit
                            }

                            override fun getAcceptedIssuers(): Array<X509Certificate?> {
                                return emptyArray<X509Certificate?>()
                            }
                        }
                    }
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
        provide<MeteringPointsService> {
            MeteringPointsApi(
                meteringPointsApiConfig = resolve(),
                client = resolve("meteringPointsHttpClient")
            )
        }
    }
}
