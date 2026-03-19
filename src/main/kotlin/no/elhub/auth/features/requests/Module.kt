package no.elhub.auth.features.requests

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.grants.common.ExposedGrantPropertiesRepository
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.common.ExposedRequestPropertiesRepository
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.Handler as CreateHandler
import no.elhub.auth.features.requests.create.route as createRoute
import no.elhub.auth.features.requests.get.Handler as GetHandler
import no.elhub.auth.features.requests.get.route as getRoute
import no.elhub.auth.features.requests.query.Handler as QueryHandler
import no.elhub.auth.features.requests.query.route as queryRoute
import no.elhub.auth.features.requests.update.Handler as UpdateHandler
import no.elhub.auth.features.requests.update.route as updateRoute

const val REQUESTS_PATH = "/access/v0/authorization-requests"

fun Application.module() {
    dependencies {
        provide<GrantRepository> { ExposedGrantRepository(resolve(), resolve()) }
        provide<RequestRepository> { ExposedRequestRepository(resolve(), resolve()) }
        provide<RequestPropertiesRepository> { ExposedRequestPropertiesRepository() }
        provide<GrantPropertiesRepository> { ExposedGrantPropertiesRepository() }
        provide<ProxyRequestBusinessHandler> { ProxyRequestBusinessHandler(resolve(), resolve()) }
        provide<UpdateHandler> { UpdateHandler(resolve(), resolve(), resolve(), resolve()) }
        provide<CreateHandler> { CreateHandler(resolve(), resolve(), resolve(), resolve()) }
        provide<GetHandler> { GetHandler(resolve(), resolve()) }
        provide<QueryHandler> { QueryHandler(resolve(), resolve()) }
    }

    val updateHandler: UpdateHandler by dependencies
    val createHandler: CreateHandler by dependencies
    val getHandler: GetHandler by dependencies
    val queryHandler: QueryHandler by dependencies
    val authorizationProvider: AuthorizationProvider by dependencies

    routing {
        route(REQUESTS_PATH) {
            updateRoute(updateHandler, authorizationProvider)
            createRoute(createHandler, authorizationProvider)
            getRoute(getHandler, authorizationProvider)
            queryRoute(queryHandler, authorizationProvider)
        }
    }
}
