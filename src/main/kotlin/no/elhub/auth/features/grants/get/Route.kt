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
import no.elhub.auth.features.grants.common.dto.toSingleGrantResponse
import org.slf4j.LoggerFactory
import java.util.UUID

const val GRANT_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get("/{$GRANT_ID_PARAM}") {
        val authorizedParty = authProvider.authorizeAll(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val id: UUID = validateId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = when (authorizedParty) {
            is AuthorizedParty.OrganizationEntity -> Query(
                id = id,
                authorizedParty = AuthorizationParty(
                    resourceId = authorizedParty.gln,
                    type = PartyType.OrganizationEntity
                )
            )

            is AuthorizedParty.Person -> Query(
                id = id,
                authorizedParty = AuthorizationParty(
                    resourceId = authorizedParty.id.toString(),
                    type = PartyType.Person
                )
            )

            is AuthorizedParty.System -> Query(
                id = id,
                authorizedParty = AuthorizationParty(
                    resourceId = authorizedParty.id,
                    type = PartyType.System
                )
            )
        }

        val grant = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization grant: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        call.respond(HttpStatusCode.OK, grant.toSingleGrantResponse())
    }
}
