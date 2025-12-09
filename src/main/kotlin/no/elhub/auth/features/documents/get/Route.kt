package no.elhub.auth.features.documents.get

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.*

const val DOCUMENT_ID_PARAM = "id"

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get("/{$DOCUMENT_ID_PARAM}") {
        val resolvedActor = authProvider.authorizeMarketParty(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@get
            }

        val id: UUID = validateId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val requestedBy = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = resolvedActor.gln)
        val document = handler(Query(documentId = id, requestedByIdentifier = requestedBy))
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
        val resolvedActor = authProvider.authorizeMarketParty(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@get
            }

        val id: UUID = validateId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val requestedBy = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = resolvedActor.gln)
        val document = handler(Query(documentId = id, requestedByIdentifier = requestedBy))
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
