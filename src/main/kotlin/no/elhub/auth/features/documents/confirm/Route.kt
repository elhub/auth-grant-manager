package no.elhub.auth.features.documents.confirm

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.config.ID

fun Route.confirmDocumentRoute(handler: ConfirmDocumentHandler) {
    patch("/{$ID}") {
        call.respond(HttpStatusCode.NotImplemented)
    }
}
