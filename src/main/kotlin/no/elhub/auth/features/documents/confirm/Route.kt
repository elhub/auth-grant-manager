package no.elhub.auth.features.documents.confirm

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.elhub.auth.config.ID
import java.util.*

fun Route.confirmDocumentRoute(handler: ConfirmDocumentHandler) {
    route("/{$ID}") {
        patch {
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}
