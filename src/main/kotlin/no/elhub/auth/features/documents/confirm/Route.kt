package no.elhub.auth.features.documents.confirm

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch

const val DOCUMENT_ID_PARAM = "id"

fun Route.confirmDocumentRoute(handler: ConfirmDocumentHandler) {
    patch("/{${DOCUMENT_ID_PARAM}") {
        call.respond(HttpStatusCode.NotImplemented)
    }
}
