package no.elhub.auth.features.grants

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.grants.get.GetGrantHandler
import no.elhub.auth.features.grants.get.getGrantRoute
import no.elhub.auth.features.grants.getScopes.Handler as GetScopesHandler
import no.elhub.auth.features.grants.getScopes.route as getScopesRoute
import no.elhub.auth.features.grants.query.Handler as QueryHandler
import no.elhub.auth.features.grants.query.route as queryRoute
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule

const val GRANTS_PATH = "/authorization-grants"

fun Application.module() {
    koinModule {
        singleOf(::ExposedGrantRepository) bind GrantRepository::class
        singleOf(::GetGrantHandler)
        singleOf(::GetScopesHandler)
        singleOf(::QueryHandler)
    }

    routing {
        route(GRANTS_PATH) {
            getGrantRoute(get())
            getScopesRoute(get())
            queryRoute(get())
        }
    }
}
