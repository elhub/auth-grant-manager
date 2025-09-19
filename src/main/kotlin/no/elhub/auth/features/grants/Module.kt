package no.elhub.auth.features.grants

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.grants.get.GetGrantHandler
import no.elhub.auth.features.grants.get.getGrantRoute
import no.elhub.auth.features.grants.getScopes.GetGrantScopesHandler
import no.elhub.auth.features.grants.getScopes.getGrantScopesRoute
import no.elhub.auth.features.grants.query.QueryGrantsHandler
import no.elhub.auth.features.grants.query.queryGrantsRoute
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule

const val GRANTS_PATH = "/authorization-grants"

fun Application.module() {
    koinModule {
        singleOf(::ExposedGrantRepository) bind GrantRepository::class
        singleOf(::GetGrantHandler)
        singleOf(::GetGrantScopesHandler)
        singleOf(::QueryGrantsHandler)
    }

    routing {
        route(GRANTS_PATH) {
            getGrantRoute(get())
            getGrantScopesRoute(get())
            queryGrantsRoute(get())
        }
    }
}
