package no.elhub.auth.services.grants

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.grants(routePath: String, grantService: AuthorizationGrantService) {
    route(routePath) {
        get {
            grantService.getGrants(call)
        }
    }
}

fun Route.grantById(routePath: String, grantService: AuthorizationGrantService) {
    route(routePath) {
        get {
            grantService.getGrantById(call)
        }
    }
}
