package no.elhub.auth.features.documents.get

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.*

const val DOCUMENT_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    get("/{$DOCUMENT_ID_PARAM}") {
        val id: UUID = validateId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val document = handler(Query(id))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(
            status = HttpStatusCode.OK,
            message = document.toGetResponse()
        )
    }

    get("/{$DOCUMENT_ID_PARAM}.pdf") {
        val id: UUID = validateId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val document = handler(Query(id))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respondBytes(
            bytes = document.file,
            contentType = ContentType.Application.Pdf
        )
    }
}
