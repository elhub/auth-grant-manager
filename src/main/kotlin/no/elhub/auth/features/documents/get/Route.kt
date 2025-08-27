package no.elhub.auth.features.documents.get

import arrow.core.getOrElse
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
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.validateId
import java.util.*

fun Route.getDocumentRoute(handler: GetDocumentHandler) {
    route(ID) {
        get {
            val id: UUID =
                validateId(call.parameters[ID]).getOrElse { error ->
                    call.respond(HttpStatusCode.fromValue(error.status), ApiErrorJson.from(error, call.url()))
                    return@get
                }

            val pdf = handler(GetDocumentQuery(id)).fold(
                ifLeft = { error ->
                    when (error) {
                        is GetDocumentProblem.NotFoundError -> call.respond(
                            HttpStatusCode.NotFound,
                            "Document not found"
                        )

                        is GetDocumentProblem.IOError -> call.respond(
                            HttpStatusCode.InternalServerError,
                            "An error occurred when attempting to retrieve the document from the database"
                        )
                    }
                },
                ifRight = { document ->
                    call.respond(HttpStatusCode.OK, message = document)
                },
            )
        }

    }

    route("{$ID}.pdf") {
        get {
            val id: UUID =
                validateId(call.parameters[ID]).getOrElse { error ->
                    call.respond(HttpStatusCode.fromValue(error.status), ApiErrorJson.from(error, call.url()))
                    return@get
                }

            val pdf = handler(GetDocumentQuery(id)).fold(
                ifLeft = { error ->
                    when (error) {
                        is GetDocumentProblem.NotFoundError -> call.respond(
                            HttpStatusCode.NotFound,
                            "Document not found"
                        )

                        is GetDocumentProblem.IOError -> call.respond(
                            HttpStatusCode.InternalServerError,
                            "An error occurred when attempting to retrieve the document from the database"
                        )
                    }
                },
                ifRight = { document ->
                    call.respondBytes(document.pdfBytes, ContentType.Application.Pdf)
                },
            )
        }
    }
}

