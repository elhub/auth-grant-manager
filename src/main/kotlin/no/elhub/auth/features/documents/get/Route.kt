package no.elhub.auth.features.documents.get

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.documents.common.toResponse
import java.util.*

fun Route.getDocumentRoute(handler: GetDocumentHandler) {
    route(ID) {
        get {
            val id: UUID = validateId(call.parameters[ID])
                .getOrElse { err ->
                    call.respond(HttpStatusCode.BadRequest, err.toApiErrorResponse())
                    return@get
                }

            val document = handler(GetDocumentQuery(id)).getOrElse { error ->
                when (error) {
                    is QueryError.ResourceNotFoundError -> call.respond(
                        HttpStatusCode.NotFound,
                        "Document not found"
                    )

                    is QueryError.IOError -> call.respond(
                        HttpStatusCode.InternalServerError,
                        "An error occurred when attempting to retrieve the document from the database"
                    )
                }
                return@get
            }

            call.respond(HttpStatusCode.OK, document.toResponse())
        }
    }

    route("$ID.pdf") {
        get {
            val id: UUID = validateId(call.parameters[ID])
                .getOrElse { err ->
                    call.respond(HttpStatusCode.BadRequest, err.toApiErrorResponse())
                    return@get
                }

            val document = handler(GetDocumentQuery(id)).getOrElse { error ->
                when (error) {
                    is QueryError.ResourceNotFoundError -> call.respond(
                        HttpStatusCode.NotFound,
                        "Document not found"
                    )

                    is QueryError.IOError -> call.respond(
                        HttpStatusCode.InternalServerError,
                        "An error occurred when attempting to retrieve the document from the database"
                    )
                }
                return@get
            }

            call.respondBytes(document.pdfBytes, ContentType.Application.Pdf)
        }
    }
}

