package no.elhub.auth.features.grants.getscopes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validatePathId
import no.elhub.auth.features.grants.common.dto.toResponse
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger(Route::class.java)
const val GRANT_ID_PARAM = "id"

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get("/{$GRANT_ID_PARAM}/scopes") {
        val authorizedParty = authProvider.authorizeAll(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val grantId: UUID = validatePathId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = Query(id = grantId, authorizedParty = authorizedParty)

        val scopes = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization grant scopes: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        call.respond(HttpStatusCode.OK, scopes.toResponse(grantId.toString()))
    }
}
