package no.elhub.auth.features.documents.query

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.*

fun Route.queryDocumentsRoute(handler: QueryDocumentsHandler) {
    get {
        call.respond(HttpStatusCode.NotImplemented)
    }
}

