package no.elhub.auth.features.requests.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.features.common.QueryError

fun Route.queryRequestRoute(handler: QueryRequestsHandler) {
    get {
        TODO("Build query from payload")
        val requests = handler(QueryRequestsQuery()).getOrElse { error ->
            when (error) {
                is QueryError.ResourceNotFoundError ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Unexpected error occurred during fetch authorization requests",
                    )

                is QueryError.IOError ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Authorization requests not found",
                    )
            }
            return@get
        }

        call.respond(HttpStatusCode.OK, requests.toResponse())
    }
}
