package no.elhub.auth.v1.api.documents.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import no.elhub.auth.common.respondError
import no.elhub.auth.v0.features.common.InputError
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toUnsupportedErrorResponse
import no.elhub.auth.v1.api.common.validatePathId
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.features.documents.ConfirmDocumentHandler

private const val PDF_MAX_BYTES = 1L * 1024L * 1024L

fun Route.route(handler: ConfirmDocumentHandler) {
    put("/{id}.pdf") {
        call.validateQueryParameters()

        if (!ContentType.Application.Pdf.match(call.request.contentType())) {
            val (status, body) = toUnsupportedErrorResponse(
                detail = "Unsupported media type, 'application/pdf' is supported.",
            )
            call.respondError(status, body)
            return@put
        }

        val id = validatePathId(call.parameters["id"])

        // Read one extra byte to detect oversized uploads without buffering the entire body.
        val signedDocument = call.receiveChannel().readBuffer(PDF_MAX_BYTES + 1).readByteArray()
        if (signedDocument.size > PDF_MAX_BYTES) {
            val (status, body) = InputError.ContentTooLargeError(
                detail = "Signed PDF upload size is limited to 1 MB",
            ).toApiErrorResponse()
            call.respondError(status, body)
            return@put
        }

        if (signedDocument.isEmpty()) {
            val (status, body) = InputError.MissingInputError.toApiErrorResponse()
            call.respondError(status, body)
            return@put
        }

        handler(id, call.authorizedAgent, signedDocument)
        call.respond(HttpStatusCode.NoContent)
    }
}
