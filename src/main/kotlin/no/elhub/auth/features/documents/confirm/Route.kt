package no.elhub.auth.features.documents.confirm

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

const val DOCUMENT_ID_PARAM = "id"

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    put("/{$DOCUMENT_ID_PARAM}.pdf") {
        val resolvedActor = authProvider.authorizeMaskinporten(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@put
            }

        val documentId = validateId(call.parameters[DOCUMENT_ID_PARAM])
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

        val requestedBy = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = resolvedActor.gln)
        handler(
            Command(
                documentId = documentId,
                requestedByIdentifier = requestedBy,
                signedFile = signedDocument
            )
        ).getOrElse { error ->
            when (error) {
                ConfirmDocumentError.DocumentNotFoundError -> call.respond(HttpStatusCode.NotFound)

                ConfirmDocumentError.InvalidRequestedByError -> call.respond(HttpStatusCode.Forbidden)

                ConfirmDocumentError.DocumentReadError,
                ConfirmDocumentError.DocumentUpdateError,
                ConfirmDocumentError.ScopeReadError,
                ConfirmDocumentError.GrantCreationError,
                ConfirmDocumentError.RequestedByResolutionError -> call.respond(HttpStatusCode.InternalServerError)

                ConfirmDocumentError.IllegalStateError ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonApiErrorObject(
                            status = "400",
                            title = "Invalid Status State",
                            detail = "Document must be in 'Pending' status to confirm."
                        )
                    )

                ConfirmDocumentError.ExpiredError ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonApiErrorObject(
                            status = "400",
                            title = "Request Has Expired",
                            detail = "Request validity period has passed"
                        )
                    )
            }
            return@put
        }

        call.respond(HttpStatusCode.NoContent)
    }
}
