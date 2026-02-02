package no.elhub.auth.features.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.common.person.ApiPersonService
import no.elhub.auth.features.common.person.PersonApiConfig
import no.elhub.auth.features.common.person.PersonService
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.ktor.plugin.koinModule

fun Application.commonModule() {
    koinModule {
        single { environment.config }
        single(named("commonHttpClient")) {
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
                        },
                    )
                }
                install(Logging) {
                    format = LoggingFormat.OkHttp
                    level = LogLevel.INFO
                }
            }
        }

        single {
            val cfg = get<ApplicationConfig>().config("authPersons")
            PersonApiConfig(
                baseUri = cfg.property("baseUri").getString(),
            )
        }

        single {
            val pdpBaseUrl = get<ApplicationConfig>().property("pdp.baseUrl").getString()
            PDPAuthorizationProvider(httpClient = get(named("commonHttpClient")), pdpBaseUrl = pdpBaseUrl)
        } bind AuthorizationProvider::class

        singleOf(::ExposedPartyRepository) bind PartyRepository::class
        single { ApiPersonService(cfg = get(), client = get(named("commonHttpClient"))) } bind PersonService::class
        singleOf(::PartyService) bind PartyService::class
    }
}
