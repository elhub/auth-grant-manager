package no.elhub.auth.features.requests

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.shouldRegisterEndpoint
import org.koin.ktor.ext.inject
import no.elhub.auth.features.requests.confirm.Handler as ConfirmHandler
import no.elhub.auth.features.requests.confirm.route as confirmRoute
import no.elhub.auth.features.requests.create.Handler as CreateHandler
import no.elhub.auth.features.requests.create.route as createRoute
import no.elhub.auth.features.requests.get.Handler as GetHandler
import no.elhub.auth.features.requests.get.route as getRoute
import no.elhub.auth.features.requests.query.Handler as QueryHandler
import no.elhub.auth.features.requests.query.route as queryRoute

const val REQUESTS_PATH = "/authorization-requests"

fun Application.configureRequestsRouting() {
    routing {
        route(REQUESTS_PATH) {
            shouldRegisterEndpoint {
                val confirmHandler: ConfirmHandler by inject()
                val createHandler: CreateHandler by inject()
                val getHandler: GetHandler by inject()
                val queryHandler: QueryHandler by inject()

                confirmRoute(confirmHandler)
                createRoute(createHandler)
                getRoute(getHandler)
                queryRoute(queryHandler)
            }
        }
    }
}
