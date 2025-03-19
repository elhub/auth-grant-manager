package no.elhub.auth.services.requests

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.elhub.auth.config.ID

fun Route.requests(routePath: String, requestService: AuthorizationRequestService) {
    route(routePath) {
        get {
            requestService.getRequests(call)
        }
        post {
            requestService.postRequest(call)
        }
    }
    route("$routePath/$ID") {
        get {
            requestService.getRequestById(call)
        }
    }
}
