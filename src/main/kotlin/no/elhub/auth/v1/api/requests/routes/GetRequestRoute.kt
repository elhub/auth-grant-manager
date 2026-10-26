package no.elhub.auth.v1.api.requests.routes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v0.features.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.validatePathId
import no.elhub.auth.v1.api.requests.response.toGetResponse
import no.elhub.auth.v1.features.requests.GetRequestHandler

fun Route.route(handler: GetRequestHandler) {
    get("/{id}") {
        val id = validatePathId(call.parameters["id"])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val request = handler(id, call.authorizedParty)
        call.respond(HttpStatusCode.OK, request.toGetResponse())
    }
}
