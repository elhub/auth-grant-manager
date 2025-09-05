package no.elhub.auth.features.requests.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.queryRequestRoute(handler: QueryRequestsHandler) {
    get {
        // TODO: Build query from payload"
        val requests = handler(QueryRequestsQuery())
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }
        call.respond(HttpStatusCode.OK, requests.toResponse())
    }
}
