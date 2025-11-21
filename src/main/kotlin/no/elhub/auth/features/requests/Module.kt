package no.elhub.auth.features.requests

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import no.elhub.auth.features.common.ExposedPartyRepository
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.shouldRegisterEndpoint
import no.elhub.auth.features.requests.common.ExposedRequestPropertiesRepository
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.common.PersonApiConfig
 import no.elhub.auth.features.common.PersonService
import no.elhub.auth.features.common.ApiPersonService
import no.elhub.auth.features.common.PartyIdentifierResolver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule
import no.elhub.auth.features.requests.confirm.Handler as ConfirmHandler
import no.elhub.auth.features.requests.confirm.route as confirmRoute
import no.elhub.auth.features.requests.create.Handler as CreateHandler
import no.elhub.auth.features.requests.create.route as createRoute
import no.elhub.auth.features.requests.get.Handler as GetHandler
import no.elhub.auth.features.requests.get.route as getRoute
import no.elhub.auth.features.requests.query.Handler as QueryHandler
import no.elhub.auth.features.requests.query.route as queryRoute

const val REQUESTS_PATH = "/authorization-requests"

fun Application.module() {
    koinModule {
        single { environment.config }

        factory {
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

        singleOf(::ExposedRequestRepository) bind RequestRepository::class
        singleOf(::ExposedRequestPropertiesRepository) bind RequestPropertiesRepository::class
        singleOf(::ExposedPartyRepository) bind PartyRepository::class
        singleOf(::ApiPersonService) bind PersonService::class
        singleOf(::PartyIdentifierResolver)
        singleOf(::ConfirmHandler)
        singleOf(::CreateHandler)
        singleOf(::GetHandler)
        singleOf(::QueryHandler)
    }

    routing {
        route(REQUESTS_PATH) {
            shouldRegisterEndpoint {
                confirmRoute(get())
                createRoute(get())
                getRoute(get())
                queryRoute(get())
            }
        }
    }
}
