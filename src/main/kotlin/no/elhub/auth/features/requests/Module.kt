package no.elhub.auth.features.requests

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.common.shouldRegisterEndpoint
import no.elhub.auth.features.requests.common.ExposedRequestPropertiesRepository
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
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

const val REQUESTS_PATH = "/access/v0/authorization-requests"

fun Application.module() {
    koinModule {
        single { environment.config }
        singleOf(::ExposedRequestRepository) bind RequestRepository::class
        singleOf(::ExposedRequestPropertiesRepository) bind RequestPropertiesRepository::class
        singleOf(::ChangeOfSupplierBusinessHandler)
        singleOf(::ProxyRequestBusinessHandler)
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
