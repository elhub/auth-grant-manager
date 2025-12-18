package no.elhub.auth.features.grants.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.grants.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

const val GRANT_ID_PARAM = "id"

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get("/{$GRANT_ID_PARAM}") {
        val authorizedParty = authProvider.authorize(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val id: UUID = validateId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val query = when (authorizedParty) {
            is AuthorizedParty.AuthorizedOrganizationEntity -> Query.GrantedTo(
                id = id,
                grantedTo = AuthorizationParty(
                    resourceId = authorizedParty.gln,
                    type = PartyType.OrganizationEntity
                )
            )

            is AuthorizedParty.AuthorizedPerson -> Query.GrantedFor(
                id = id,
                grantedFor = AuthorizationParty(
                    resourceId = authorizedParty.id.toString(),
                    type = PartyType.Person
                )
            )
        }

        val grant = handler(query)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, grant.toResponse())
    }
}
