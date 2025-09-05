package no.elhub.auth.features.grants.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.grants.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.queryGrantsRoute(handler: QueryGrantsHandler) {
    get {
        // TODO: Build query from payload
        val grants = handler(QueryGrantsQuery())
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, grants.toResponse())
    }
}
