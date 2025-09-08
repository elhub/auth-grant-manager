package no.elhub.auth.features.requests.confirm

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch

fun Route.confirmRequestRoute(handler: ConfirmRequestHandler) {
    patch {
        call.respond(HttpStatusCode.NotImplemented)
    }
}
