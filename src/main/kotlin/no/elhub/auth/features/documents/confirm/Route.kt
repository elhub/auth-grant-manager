package no.elhub.auth.features.documents.confirm

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.auth.authorizedParty
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.toUnsupportedErrorResponse
import no.elhub.auth.features.common.validatePathId
import org.slf4j.LoggerFactory

const val DOCUMENT_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler) {
    put("/{$DOCUMENT_ID_PARAM}.pdf") {
        if (!ContentType.Application.Pdf.match(call.request.contentType())) {
            val (status, error) = toUnsupportedErrorResponse(detail = "Unsupported media type, 'application/pdf' is supported.")
            call.respond(status, error)
            return@put
        }

        val documentId = validatePathId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@put
            }

        val signedDocument = call.receiveChannel().readRemaining().readByteArray()
        if (signedDocument.isEmpty()) {
            val (status, body) = InputError.MissingInputError.toApiErrorResponse()
            call.respond(status, body)
            return@put
        }

        handler(
            Command(
                documentId = documentId,
                authorizedParty = call.authorizedParty,
                signedFile = signedDocument
            )
        ).getOrElse { error ->
            logger.error("Failed to confirm authorization document: {}", error)
            val (status, validationError) = error.toApiErrorResponse()
            call.respond(status, validationError)
            return@put
        }

        call.respond(HttpStatusCode.NoContent)
    }
}
