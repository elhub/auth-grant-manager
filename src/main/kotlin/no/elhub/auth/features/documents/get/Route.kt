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
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.*

fun Route.getDocumentRoute(handler: GetDocumentHandler) {
    route(ID) {
        get {
            val id: UUID = validateId(call.parameters[ID])
                .getOrElse { err ->
                    val (status, body) = err.toApiErrorResponse()
                    call.respond(status, JsonApiErrorCollection(listOf(body)))
                    return@get
                }

            val document = handler(GetDocumentQuery(id))
                .getOrElse { err ->
                    val (status, body) = err.toApiErrorResponse()
                    call.respond(status, JsonApiErrorCollection(listOf(body)))
                    return@get
                }

            call.respond(HttpStatusCode.OK, document.toResponse())
        }
    }

    route("{$ID}.pdf") {
        get {
            val id: UUID = validateId(call.parameters[ID])
                .getOrElse { err ->
                    val (status, body) = err.toApiErrorResponse()
                    call.respond(status, JsonApiErrorCollection(listOf(body)))
                    return@get
                }

            val document = handler(GetDocumentQuery(id))
                .getOrElse { err ->
                    val (status, body) = err.toApiErrorResponse()
                    call.respond(status, JsonApiErrorCollection(listOf(body)))
                    return@get
                }

            call.respondBytes(document.pdfBytes, ContentType.Application.Pdf)
        }
    }
}

