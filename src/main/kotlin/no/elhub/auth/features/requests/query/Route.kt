package no.elhub.auth.features.requests.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.requests.common.toResponses
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.route(handler: Handler) {
    get {
        // TODO: Build query from payload"
        val requests = handler(Query())
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }
        call.respond(HttpStatusCode.OK, requests.toResponses())
    }
}
