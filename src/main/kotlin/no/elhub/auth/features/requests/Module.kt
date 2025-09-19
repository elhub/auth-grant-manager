package no.elhub.auth.features.requests

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.API_PATH
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.confirm.ConfirmRequestHandler
import no.elhub.auth.features.requests.confirm.confirmRequestRoute
import no.elhub.auth.features.requests.create.CreateRequestHandler
import no.elhub.auth.features.requests.create.createRequestRoute
import no.elhub.auth.features.requests.get.GetRequestHandler
import no.elhub.auth.features.requests.get.getRequestRoute
import no.elhub.auth.features.requests.query.QueryRequestsHandler
import no.elhub.auth.features.requests.query.queryRequestRoute
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule

const val REQUESTS_PATH = "$API_PATH/authorization-requests"

fun Application.module() {
    koinModule {
        singleOf(::ExposedRequestRepository) bind RequestRepository::class
        singleOf(::ConfirmRequestHandler)
        singleOf(::CreateRequestHandler)
        singleOf(::GetRequestHandler)
        singleOf(::QueryRequestsHandler)
    }

    routing {
        route(REQUESTS_PATH) {
            confirmRequestRoute(get())
            createRequestRoute(get(), get())
            getRequestRoute(get())
            queryRequestRoute(get())
        }
    }
}
