package no.elhub.auth.features.grants

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.shouldRegisterEndpoint
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.grants.common.GrantRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule
import no.elhub.auth.features.grants.consume.Handler as ConsumeHandler
import no.elhub.auth.features.grants.consume.route as consumeRoute
import no.elhub.auth.features.grants.get.Handler as GetHandler
import no.elhub.auth.features.grants.get.route as getRoute
import no.elhub.auth.features.grants.getScopes.Handler as GetScopesHandler
import no.elhub.auth.features.grants.getScopes.route as getScopesRoute
import no.elhub.auth.features.grants.query.Handler as QueryHandler
import no.elhub.auth.features.grants.query.route as queryRoute

const val GRANTS_PATH = "/authorization-grants"

fun Application.module() {
    koinModule {
        singleOf(::ExposedPartyRepository) bind PartyRepository::class
        singleOf(::ExposedGrantRepository) bind GrantRepository::class
        singleOf(::GetHandler)
        singleOf(::GetScopesHandler)
        singleOf(::QueryHandler)
        singleOf(::ConsumeHandler)
    }

    routing {
        route(GRANTS_PATH) {
            shouldRegisterEndpoint {
                getRoute(get())
                getScopesRoute(get())
                queryRoute(get())
                consumeRoute(get())
            }
        }
    }
}
