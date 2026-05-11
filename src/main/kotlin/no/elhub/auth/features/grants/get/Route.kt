package no.elhub.auth.features.grants.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.authorizedParty
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validatePathId
import no.elhub.auth.features.grants.common.dto.toSingleGrantResponse
import org.slf4j.LoggerFactory
import java.util.UUID

const val GRANT_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler) {
    get("/{$GRANT_ID_PARAM}") {
        val id: UUID = validatePathId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = Query(id = id, authorizedParty = call.authorizedParty)

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
