package no.elhub.auth.features.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.person.ApiPersonService
import no.elhub.auth.features.common.person.PersonApiConfig
import no.elhub.auth.features.common.person.PersonService
import org.slf4j.LoggerFactory

fun Application.commonModule() {
    val appEnvironment = environment
    dependencies {
        provide<ApplicationConfig> {
            appEnvironment.config
        }
        provide<HttpClient>(name = "commonHttpClient") {
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        }
                    )
                }
                install(Logging) {
                    format = LoggingFormat.OkHttp
                    level = LogLevel.INFO
                }
            }
        }
        provide<PersonApiConfig> {
            val cfg = resolve<ApplicationConfig>().config("authPersons")
            PersonApiConfig(baseUri = cfg.property("baseUri").getString())
        }
        provide(name = "proxyHttpClient") {
            val logger = LoggerFactory.getLogger("proxyHttpClient")
            val proxyUrl = resolve<ApplicationConfig>().property("httpProxy.url")
            HttpClient(CIO) {
                logger.info("Configuring HTTP proxy: {}", proxyUrl)
                engine { proxy = ProxyBuilder.http(proxyUrl) }

                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        }
                    )
                }
                install(Logging) {
                    format = LoggingFormat.OkHttp
                    level = LogLevel.INFO
                }
            }
        }

        provide<PDPAuthorizationProvider> {
            val pdpBaseUrl = appEnvironment.config.property("pdp.baseUrl").getString()
            PDPAuthorizationProvider(httpClient = resolve("commonHttpClient"), pdpBaseUrl = pdpBaseUrl)
        }

        provide<PartyRepository> { ExposedPartyRepository() }

        provide<PersonService> {
            ApiPersonService(cfg = resolve(), client = resolve("commonHttpClient"))
        }

        provide<PartyService> {
            PartyService(resolve())
        }
    }
}
