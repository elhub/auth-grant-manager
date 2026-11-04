package no.elhub.auth.v1.api.documents.routes

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.InputError
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toUnsupportedErrorResponse
import no.elhub.auth.v0.features.common.validatePathId
import no.elhub.auth.v1.features.documents.ConfirmDocumentHandler

private const val PDF_MAX_BYTES = 1L * 1024L * 1024L

fun Route.route(handler: ConfirmDocumentHandler) {
    put("/{id}.pdf") {
        if (!ContentType.Application.Pdf.match(call.request.contentType())) {
            val (status, body) = toUnsupportedErrorResponse(
                detail = "Unsupported media type, 'application/pdf' is supported.",
            )
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
            return@put
        }

        val id = validatePathId(call.parameters["id"]).getOrElse { error ->
            val (status, body) = error.toApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
            return@put
        }

        // Read one extra byte to detect oversized uploads without buffering the entire body.
        val signedDocument = call.receiveChannel().readBuffer(PDF_MAX_BYTES + 1).readByteArray()
        if (signedDocument.size > PDF_MAX_BYTES) {
            val (status, body) = InputError.ContentTooLargeError(
                detail = "Signed PDF upload size is limited to 1 MB",
            ).toApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
            return@put
        }

        if (signedDocument.isEmpty()) {
            val (status, body) = InputError.MissingInputError.toApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
            return@put
        }

        handler(id, call.authorizedParty, signedDocument)
        call.respond(HttpStatusCode.NoContent)
    }
}
