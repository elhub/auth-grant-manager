package no.elhub.auth.features.requests.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.authorizedParty
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validatePathId
import no.elhub.auth.features.requests.get.dto.toGetSingleResponse
import org.slf4j.LoggerFactory
import java.util.UUID

const val REQUEST_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler) {
    get("/{$REQUEST_ID_PARAM}") {
        val id: UUID = validatePathId(call.parameters[REQUEST_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = Query(id = id, authorizedParty = call.authorizedParty)

        val request = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization request: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        call.respond(HttpStatusCode.OK, request.toGetSingleResponse())
    }
}
