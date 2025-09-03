package no.elhub.auth.features.documents.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.documents.common.toResponse
import java.util.*

fun Route.queryDocumentsRoute(handler: QueryDocumentsHandler) {
    get {
        val documents = handler(QueryDocumentsQuery())
            .getOrElse { error ->
                when (error) {
                    is QueryDocumentsError.NotFoundError -> call.respond(HttpStatusCode.NotFound)
                    is QueryDocumentsError.IOError -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@get
            }

        call.respond(
            status = HttpStatusCode.OK,
            message = documents.toResponse()
        )
    }
}

