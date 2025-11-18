package no.elhub.auth.features.documents.confirm

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

const val DOCUMENT_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    patch("/{$DOCUMENT_ID_PARAM}.pdf") {
        val documentId = validateId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@patch
            }

        val signedDocument = call.receiveChannel().readRemaining().readByteArray()
        if (signedDocument.isEmpty()) {
            val (status, body) = InputError.MissingInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@patch
        }

        val result = handler(
            Command(
                documentId = documentId,
                signedFile = signedDocument
            )
        ).getOrElse { error ->
            when (error) {
                ConfirmDocumentError.DocumentNotFound -> call.respond(HttpStatusCode.NotFound)
                ConfirmDocumentError.DocumentReadError,
                ConfirmDocumentError.DocumentUpdateError,
                ConfirmDocumentError.ScopeReadError,
                ConfirmDocumentError.GrantCreationError -> call.respond(HttpStatusCode.InternalServerError)
            }
            return@patch
        }

        call.respond(HttpStatusCode.OK, result.toResponse())
    }
}
