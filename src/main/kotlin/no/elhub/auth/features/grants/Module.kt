package no.elhub.auth.features.grants

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.grants.common.ExposedGrantPropertiesRepository
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.grants.consume.Handler as ConsumeHandler
import no.elhub.auth.features.grants.consume.route as consumeRoute
import no.elhub.auth.features.grants.get.Handler as GetHandler
import no.elhub.auth.features.grants.get.route as getRoute
import no.elhub.auth.features.grants.getscopes.Handler as GetScopesHandler
import no.elhub.auth.features.grants.getscopes.route as getScopesRoute
import no.elhub.auth.features.grants.query.Handler as QueryHandler
import no.elhub.auth.features.grants.query.route as queryRoute

const val GRANTS_PATH = "/access/v0/authorization-grants"

fun Application.module() {
    dependencies {
        provide<ExposedGrantRepository> {
            ExposedGrantRepository(resolve(), resolve())
        }
        provide<ExposedGrantPropertiesRepository> {
            ExposedGrantPropertiesRepository()
        }

        provide<GetHandler> {
            GetHandler(resolve())
        }

        provide<GetScopesHandler> {
            GetScopesHandler(resolve())
        }
        provide<QueryHandler> {
            QueryHandler(resolve())
        }
        provide<ConsumeHandler> {
            ConsumeHandler(resolve())
        }
    }

    val getRouteHandler: GetHandler by dependencies
    val getScopesHandler: GetScopesHandler by dependencies
    val queryRouteHandler: QueryHandler by dependencies
    val consumerRouteHandler: ConsumeHandler by dependencies
    val authProvider: AuthorizationProvider by dependencies

    routing {
        route(GRANTS_PATH) {
            getRoute(getRouteHandler, authProvider)
            getScopesRoute(getScopesHandler, authProvider)
            queryRoute(queryRouteHandler, authProvider)
            consumeRoute(consumerRouteHandler, authProvider)
        }
    }
}
