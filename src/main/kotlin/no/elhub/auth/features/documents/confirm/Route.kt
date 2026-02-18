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
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import org.slf4j.LoggerFactory

const val DOCUMENT_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

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

        val authorizedParty = AuthorizationParty(id = resolvedActor.gln, type = PartyType.OrganizationEntity)
        handler(
            Command(
                documentId = documentId,
                authorizedParty = authorizedParty,
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
