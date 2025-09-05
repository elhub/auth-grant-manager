package no.elhub.auth.features.documents.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.documents.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.*

fun Route.queryDocumentsRoute(handler: QueryDocumentsHandler) {
    get {
        val documents = handler(QueryDocumentsQuery())
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, documents.toResponse())
    }
}

