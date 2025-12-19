package no.elhub.auth.features.grants.getScopes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.grants.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

const val GRANT_ID_PARAM = "id"

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get("/{$GRANT_ID_PARAM}/scopes") {
        val resolvedActor = authProvider.authorizeMaskinporten(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val partyIdentifier = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = resolvedActor.gln)

        val id: UUID = validateId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val scopes = handler(Query(id = id, grantedTo = partyIdentifier))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, scopes.toResponse(id.toString()))
    }
}
