package no.elhub.auth.features.requests

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.confirm.ConfirmRequestHandler
import no.elhub.auth.features.requests.confirm.confirmRequestRoute
import no.elhub.auth.features.requests.create.Handler as CreateHandler
import no.elhub.auth.features.requests.create.route as createRoute
import no.elhub.auth.features.requests.get.Handler as GetHandler
import no.elhub.auth.features.requests.get.route as getRoute
import no.elhub.auth.features.requests.query.Handler as QueryHandler
import no.elhub.auth.features.requests.query.route as queryRoute
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule

const val REQUESTS_PATH = "/authorization-requests"

fun Application.module() {
    koinModule {
        singleOf(::ExposedRequestRepository) bind RequestRepository::class
        singleOf(::ConfirmRequestHandler)
        singleOf(::CreateHandler)
        singleOf(::GetHandler)
        singleOf(::QueryHandler)
    }

    routing {
        route(REQUESTS_PATH) {
            confirmRequestRoute(get())
            createRoute(get(), get())
            getRoute(get())
            queryRoute(get())
        }
    }
}
