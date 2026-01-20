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
import no.elhub.auth.features.common.auth.toAuthErrorResponse
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.toInputErrorResponse
import no.elhub.auth.features.common.validateId
import org.slf4j.LoggerFactory

const val DOCUMENT_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    put("/{$DOCUMENT_ID_PARAM}.pdf") {
        val resolvedActor = authProvider.authorizeMaskinporten(call)
            .getOrElse {
                val error = it.toAuthErrorResponse()
                call.respond(error.first, error.second)
                return@put
            }

        val documentId = validateId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toInputErrorResponse()
                call.respond(status, body)
                return@put
            }

        val signedDocument = call.receiveChannel().readRemaining().readByteArray()
        if (signedDocument.isEmpty()) {
            val (status, body) = InputError.MissingInputError.toInputErrorResponse()
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
            logger.error("Failed to confirm authorization document: {}", error)
            val (status, error) = error.toConfirmErrorResponse()
            call.respond(status, error)
            return@put
        }

        call.respond(HttpStatusCode.NoContent)
    }
}
