package no.elhub.auth.v1

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.plugin.policies.token.base.authinfo.AuthInfoPolicy
import no.elhub.auth.plugin.tokenAuthorize
import no.elhub.auth.v1.api.grants.routes.route
import no.elhub.auth.v1.features.grants.GetGrantHandler

const val V1_GRANTS_PATH = "/access/v1/authorization-grants"

fun Application.grantsModule() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    dependencies {
        provide<GetGrantHandler> { GetGrantHandler(resolve()) }
    }

    val getHandler: GetGrantHandler by dependencies
    routing {
        tokenAuthorize(AuthInfoPolicy) {
            route(V1_GRANTS_PATH) {
                route(getHandler)
            }
        }
    }
}
