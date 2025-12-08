package no.elhub.auth.features.requests.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.get.dto.toGetResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID

const val REQUEST_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    get("/{$REQUEST_ID_PARAM}") {
        val id: UUID = validateId(call.parameters[REQUEST_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val request = handler(Query(id))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, request.toGetResponse())
    }
}
