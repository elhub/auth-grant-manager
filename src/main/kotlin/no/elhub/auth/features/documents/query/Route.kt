package no.elhub.auth.features.documents.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get {
        val resolvedActor = authProvider.authorizeMarketParty(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val partyIdentifier = PartyIdentifier(idType = PartyIdentifierType.GlobalLocationNumber, idValue = resolvedActor.gln)
        val documents = handler(Query(requestedByIdentifier = partyIdentifier))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, documents.toGetResponse())
    }
}
