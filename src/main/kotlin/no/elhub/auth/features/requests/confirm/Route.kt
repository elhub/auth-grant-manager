package no.elhub.auth.features.requests.confirm

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch

const val REQUEST_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    patch("/{$REQUEST_ID_PARAM}") {
        call.respond(HttpStatusCode.NotImplemented)
    }
}
