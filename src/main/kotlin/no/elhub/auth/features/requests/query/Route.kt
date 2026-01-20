package no.elhub.auth.features.requests.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.toAuthErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toQueryErrorResponse
import no.elhub.auth.features.requests.query.dto.toGetCollectionResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get {
        val authorizedParty = authProvider.authorizeEndUserOrMaskinporten(call)
            .getOrElse { err ->
                val (status, body) = err.toAuthErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = when (authorizedParty) {
            is AuthorizedParty.OrganizationEntity -> Query(
                authorizedParty = AuthorizationParty(
                    resourceId = authorizedParty.gln,
                    type = PartyType.OrganizationEntity
                )
            )

            is AuthorizedParty.Person -> Query(
                authorizedParty = AuthorizationParty(
                    resourceId = authorizedParty.id.toString(),
                    type = PartyType.Person
                )
            )
        }

        val requests = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization requests: {}", error)
                val (status, body) = error.toQueryErrorResponse()
                call.respond(status, body)
                return@get
            }
        call.respond(HttpStatusCode.OK, requests.toGetCollectionResponse())
    }
}
