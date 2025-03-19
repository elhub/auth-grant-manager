package no.elhub.auth.services.grants

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.elhub.auth.config.ID

fun Route.grants(routePath: String, grantService: AuthorizationGrantService) {
    route(routePath) {
        get {
            grantService.getGrants(call)
        }
    }
    route("$routePath/$ID") {
        get {
            grantService.getGrantById(call)
        }
    }
}
