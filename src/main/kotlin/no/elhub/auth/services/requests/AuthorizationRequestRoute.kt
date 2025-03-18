package no.elhub.auth.services.requests

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.requests(routePath: String, requestService: AuthorizationRequestService) {
    route(routePath) {
        get {
            requestService.getRequests(call)
        }
        post {
            requestService.postRequest(call)
        }
    }
}

fun Route.requestById(routePath: String, requestService: AuthorizationRequestService) {
    route(routePath) {
        get {
            requestService.getRequestById(call)
        }
    }
}
