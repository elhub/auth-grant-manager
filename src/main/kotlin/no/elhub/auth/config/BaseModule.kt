package no.elhub.auth.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.ApiPersonService
import no.elhub.auth.features.common.ExposedPartyRepository
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.common.PersonApiConfig
import no.elhub.auth.features.common.PersonService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val baseModule = module {
    single {
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
                level = LogLevel.ALL
            }
        }
    }

    single {
        val cfg = get<ApplicationConfig>().config("authPersons")
        PersonApiConfig(
            baseUri = cfg.property("baseUri").getString()
        )
    }

    singleOf(::ExposedPartyRepository) bind PartyRepository::class
    singleOf(::ApiPersonService) bind PersonService::class
    singleOf(::PartyService) bind PartyService::class
}
