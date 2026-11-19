package no.elhub.auth.v1.api.requests.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v1.api.common.validatePathId
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.requests.response.toGetResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.features.requests.GetRequestHandler

fun Route.route(handler: GetRequestHandler) {
    get("/{id}") {
        call.validateQueryParameters()
        val id = validatePathId(call.parameters["id"])

        val request = handler(id, call.authorizedAgent)
        call.respond(HttpStatusCode.OK, request.toGetResponse())
    }
}
